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
import tigase.halcyon.core.Scope
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

sealed class SASLEvent : Event(TYPE) { companion object {

	const val TYPE = "tigase.halcyon.core.xmpp.modules.auth.SASLEvent"
}

	data class SASLStarted(val mechanism: String) : SASLEvent()
	class SASLSuccess : SASLEvent()
	data class SASLError(val error: SASLModule.SASLError, val description: String?) : SASLEvent()
}

class SASLContext {

	var mechanism: SASLMechanism? = null
		internal set

	var state: SASLModule.State = SASLModule.State.Unknown
		internal set

	var complete = false
		internal set

	override fun toString(): String {
		return "SASLContext(mechanism=$mechanism, state=$state, complete=$complete)"
	}

}

class SASLModule(override val context: Context) : XmppModule {

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
		TemporaryAuthFailure("temporary-auth-failure");

		companion object {

			fun valueByElementName(elementName: String): SASLError? {
				return values().firstOrNull { saslError -> saslError.elementName == elementName }
			}
		}
	}

	enum class State { Unknown,
		InProgress,
		Success,
		Failed
	}

	companion object {

		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-sasl"
		const val TYPE = "tigase.halcyon.core.xmpp.modules.auth.SASLModule"
		private const val SASL_CONTEXT = "$TYPE#Context"

	}

	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.auth.SASLModule")
	override val type = TYPE
	override val criteria = Criterion.or(
		Criterion.nameAndXmlns("success", XMLNS),
		Criterion.nameAndXmlns("failure", XMLNS),
		Criterion.nameAndXmlns("challenge", XMLNS)
	)
	override val features: Array<String>? = null

	var saslContext: SASLContext by property(Scope.Connection) { SASLContext() }
		private set

	private val mechanisms: MutableList<SASLMechanism> = mutableListOf()

	override fun initialize() {
		mechanisms.add(SASLPlain())
		mechanisms.add(SASLAnonymous())
	}

	private fun selectMechanism(): SASLMechanism {
		for (mechanism in mechanisms) {
			log.finer { "Checking mechanism ${mechanism.name}" }
			if (mechanism.isAllowedToUse(context.config, saslContext)) {
				log.fine { "Selected mechanism: ${mechanism.name}" }
				return mechanism
			}
		}
		throw HalcyonException("None of known SASL mechanism is supported by server")
	}

	fun startAuth() {
		val mechanism = selectMechanism()

		val authData = mechanism.evaluateChallenge(null, context.config, saslContext)
		val authElement = element("auth") {
			xmlns = XMLNS
			attribute("mechanism", mechanism.name)
			if (authData != null) +authData
		}
		context.writer.writeDirectly(authElement)
		saslContext.mechanism = mechanism
		saslContext.state = State.InProgress
		context.eventBus.fire(SASLEvent.SASLStarted(mechanism.name))
	}

	override fun process(element: Element) {
		when (element.name) {
			"success" -> {
				saslContext.state = State.Success
				context.eventBus.fire(SASLEvent.SASLSuccess())
			}
			"failure" -> {
				val errElement = element.getFirstChild()!!
				val saslError = SASLError.valueByElementName(errElement.name)!!

				var errorText: String? = null
				element.getFirstChild("text")?.apply {
					errorText = this.value
				}

				saslContext.state = State.Failed
				context.eventBus.fire(SASLEvent.SASLError(saslError, errorText))
			}
			"challenge" -> processChallenge(saslContext, element)
			else -> throw XMPPException(ErrorCondition.BadRequest, "Unsupported element")
		}
	}

	private fun processChallenge(ctx: SASLContext, element: Element) {
		val mech = ctx.mechanism!!
		if (saslContext.complete) throw HalcyonException("Mechanism ${mech.name} is finished but Server sent challenge.")
		val v = element.value
		val r = mech.evaluateChallenge(v, context.config, saslContext)

		val authElement = element("response") {
			xmlns = XMLNS
			if (r != null) +r
		}
		context.writer.writeDirectly(authElement)
	}

	fun clear() {
		saslContext = SASLContext()
	}
}