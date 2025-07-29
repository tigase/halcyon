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
package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.Context
import tigase.halcyon.core.Scope
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.FullJID
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.auth.InlineFeatures
import tigase.halcyon.core.xmpp.modules.auth.InlineProtocol
import tigase.halcyon.core.xmpp.modules.auth.InlineProtocolStage
import tigase.halcyon.core.xmpp.modules.auth.InlineResponse
import tigase.halcyon.core.xmpp.modules.auth.whenExists
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toFullJID

/**
 * Event fired when resource binding process is finished.
 */
sealed class BindEvent : Event(TYPE) {

    companion object : EventDefinition<BindEvent> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.BindEvent"
    }

    /**
     * Bind success.
     * @param jid bound JID.
     */
    data class Success(val jid: JID, val inlineProtocol: Boolean) : BindEvent()

    /**
     * Bind failure.
     * @param error exception object.
     */
    data class Failure(val error: Throwable) : BindEvent()
}

@HalcyonConfigDsl
interface BindModuleConfig {

    var resource: String?
}

/**
 * Resource bind module. The module is integrated part of XMPP Core protocol.
 */
class BindModule(override val context: AbstractHalcyon) :
    XmppModule,
    InlineProtocol,
    BindModuleConfig {

    enum class State {

        Unknown,
        InProgress,
        Success,
        Failed
    }

    /**
     * Resource bind module. The module is integrated part of XMPP Core protocol.
     */
    companion object : XmppModuleProvider<BindModule, BindModuleConfig> {

        const val BIND2_XMLNS = "urn:xmpp:bind:0"
        const val XMLNS = "urn:ietf:params:xml:ns:xmpp-bind"
        override val TYPE = XMLNS
        override fun configure(module: BindModule, cfg: BindModuleConfig.() -> Unit) {
            module.cfg()
        }

        override fun instance(context: Context): BindModule = BindModule(context as AbstractHalcyon)
    }

    override val type = TYPE
    override val criteria: Criteria? = null
    override val features = arrayOf(XMLNS)

    @Deprecated("Moved to Context")
    var boundJID: FullJID? by context::boundJID
        internal set

    /**
     * State of bind process.
     */
    var state: State by propertySimple(Scope.Session, State.Unknown)
        internal set

    override var resource: String? = null

    /**
     * Prepare bind request.
     */
    fun bind(resource: String? = this.resource): RequestBuilder<BindResult, IQ> {
        val stanza = iq {
            type = IQType.Set
            "bind" {
                xmlns = XMLNS
                resource?.let {
                    "resource" {
                        value = it
                    }
                }
            }
        }
        return context.request.iq(stanza).onSend {
            state = State.InProgress
        }.map(this::createBindResult).response {
            it.onSuccess { success ->
                bind(success.jid, false)
            }
            it.onFailure {
                state = State.Failed
                context.eventBus.fire(BindEvent.Failure(it))
            }
        }
    }

    private fun createBindResult(element: IQ): BindResult {
        val bind = element.getChildrenNS("bind", XMLNS)!!
        val jidElement = bind.getFirstChild("jid")!!
        val jid = jidElement.value!!.toFullJID()
        return BindResult(jid)
    }

    override fun process(element: Element): Unit = throw XMPPException(ErrorCondition.BadRequest)

    private fun bind(jid: FullJID, inlineProtocol: Boolean) {
        state = State.Success
        context.boundJID = jid
        context.eventBus.fire(BindEvent.Success(jid, inlineProtocol))
    }

    data class BindResult(val jid: FullJID)

    override fun featureFor(features: InlineFeatures, stage: InlineProtocolStage): Element? =
        if (stage == InlineProtocolStage.AfterSasl) {
            val isResumptionAvailable =
                context.getModuleOrNull(StreamManagementModule)?.isResumptionAvailable() ?: false

            if (!isResumptionAvailable) {
                val bindInlineFeatures = features.subInline("bind", BIND2_XMLNS)
                element("bind") {
                    xmlns = BIND2_XMLNS
                    "tag" { +"Halcyon" }
                    context.modules.getModules().filterIsInstance<InlineProtocol>()
                        .mapNotNull {
                            it.featureFor(bindInlineFeatures, InlineProtocolStage.AfterBind)
                        }
                        .forEach { addChild(it) }
                }
            } else {
                null
            }
        } else {
            null
        }

    override fun process(response: InlineResponse) {
        response.whenExists(InlineProtocolStage.AfterSasl, "bound", BIND2_XMLNS) { boundElement ->
            bind(
                response.element.getFirstChild("authorization-identifier")!!.value!!.toFullJID(),
                true
            )

            InlineResponse(InlineProtocolStage.AfterBind, boundElement).let { response ->
                context.modules.getModules().filterIsInstance<InlineProtocol>()
                    .forEach { consumer -> consumer.process(response) }
            }
        }
    }
}
