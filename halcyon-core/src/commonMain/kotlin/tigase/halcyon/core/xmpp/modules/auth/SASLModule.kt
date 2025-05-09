/*
 * halcyon-core
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

class ClientSaslException : HalcyonException {

    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}

@HalcyonConfigDsl
interface SASLModuleConfig {

    var enabled: Boolean

    /**
     *
     */
    fun mechanisms(clear: Boolean = false, init: MechanismsConfiguration.() -> Unit)
}

class SASLModule(override val context: Context) :
    XmppModule,
    SASLModuleConfig {

    enum class SASLError(val elementName: String) {

        /**
         * The receiving entity acknowledges an &lt;abort/&gt; element sent by
         * the initiating entity; sent in reply to the &lt;abort/&gt; element.
         */
        Aborted("aborted"),

        /**
         * The data provided by the initiating entity could not be processed
         * because the BASE64 encoding is incorrect (e.g., because the encoding
         * does not adhere to the definition in Section 3 of BASE64); sent in
         * reply to a &lt;response/&gt; element or an &lt;auth/&gt; element with
         * initial response data.
         */
        IncorrectEncoding("incorrect-encoding"),

        /**
         * The authzid provided by the initiating entity is invalid, either
         * because it is incorrectly formatted or because the initiating entity
         * does not have permissions to authorize that ID; sent in reply to a
         * &lt;response/&gt element or an &lt;auth/&gt element with initial
         * response data.
         */
        InvalidAuthzid("invalid-authzid"),

        /**
         * The initiating entity did not provide a mechanism or requested a
         * mechanism that is not supported by the receiving entity; sent in
         * reply to an &lt;auth/&gt element.
         */
        InvalidMechanism("invalid-mechanism"),

        /**
         * The mechanism requested by the initiating entity is weaker than
         * server policy permits for that initiating entity; sent in reply to a
         * &lt;response/&gt element or an &lt;auth/&gt element with initial
         * response data.
         */
        MechanismTooWeak("mechanism-too-weak"),

        /**
         * he authentication failed because the initiating entity did not
         * provide valid credentials (this includes but is not limited to the
         * case of an unknown username); sent in reply to a &lt;response/&gt
         * element or an &lt;auth/&gt element with initial response data.
         */
        NotAuthorized("not-authorized"),
        ServerNotTrusted("server-not-trusted"),

        /**
         * The authentication failed because of a temporary error condition
         * within the receiving entity; sent in reply to an &lt;auth/&gt element
         * or &lt;response/&gt element.
         */
        TemporaryAuthFailure("temporary-auth-failure"),

        Unknown("-");

        companion object {

            fun valueByElementName(elementName: String): SASLError? =
                values().firstOrNull { saslError ->
                    saslError.elementName == elementName
                }
        }
    }

    companion object : XmppModuleProvider<SASLModule, SASLModuleConfig> {

        const val XMLNS = "urn:ietf:params:xml:ns:xmpp-sasl"
        override val TYPE = "tigase.halcyon.core.xmpp.modules.auth.SASLModule"
        override fun configure(module: SASLModule, cfg: SASLModuleConfig.() -> Unit) = module.cfg()

        override fun instance(context: Context): SASLModule = SASLModule(context)
    }

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.auth.SASLModule")
    override val type = TYPE
    override val criteria = Criterion.or(
        Criterion.nameAndXmlns("success", XMLNS),
        Criterion.nameAndXmlns("failure", XMLNS),
        Criterion.nameAndXmlns("challenge", XMLNS)
    )
    override val features: Array<String>? = null

    private val engine = SASLEngine(context)

    val saslContext: SASLContext by engine::saslContext

    override var enabled: Boolean = true

    override fun mechanisms(clear: Boolean, init: MechanismsConfiguration.() -> Unit) {
        if (clear) engine.removeAllMechanisms()
        engine.init()
    }

    fun startAuth(streamFeatures: Element) {
        val authData = engine.start(allowedMechanisms(streamFeatures), streamFeatures)
        val authElement = element("auth") {
            xmlns = XMLNS
            attribute("mechanism", authData.mechanismName)
            if (authData.data != null) +authData.data
        }
        context.writer.writeDirectly(authElement)
    }

    override fun process(element: Element) {
        try {
            when (element.name) {
                "success" -> engine.evaluateSuccess(element.value)
                "failure" -> processFailure(element)
                "challenge" -> processChallenge(element)
                else -> throw XMPPException(ErrorCondition.BadRequest, "Unsupported element")
            }
        } catch (e: ClientSaslException) {
            engine.saslContext.state = State.Failed
            context.eventBus.fire(SASLEvent.SASLError(SASLError.Unknown, e.message))
        }
    }

    private fun processChallenge(element: Element) {
        val v = element.value
        val r = engine.evaluateChallenge(v)

        val authElement = element("response") {
            xmlns = XMLNS
            if (r != null) +r
        }
        context.writer.writeDirectly(authElement)
    }

    private fun processFailure(element: Element) {
        val errElement = element.getFirstChild()!!
        val saslError = SASLError.valueByElementName(errElement.name)!!

        var errorText: String? = null
        element.getFirstChild("text")?.apply {
            errorText = this.value
        }
        engine.evaluateFailure(saslError, errorText)
    }

    private fun allowedMechanisms(streamFeatures: Element): List<String> =
        streamFeatures.getChildrenNS("mechanisms", XMLNS)?.children?.filter {
            it.name == "mechanism"
        }?.mapNotNull { it.value } ?: emptyList()

    fun isAllowed(streamFeatures: Element): Boolean = context.config.sasl != null &&
        enabled &&
        engine.checkMechanisms(allowedMechanisms(streamFeatures))
}
