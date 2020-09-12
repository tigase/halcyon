/*
 * Tigase Halcyon XMPP Library
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
package tigase.halcyon.core.xmpp.modules.jingle

import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.IQRequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.MessageType

class Jingle {
    enum class Action(val value: String) {
        contentAccept("content-accept"),
        contentAdd("content-add"),
        contentModify("content-modify"),
        contentReject("content-reject"),
        descriptionInfo("description-info"),
        securityInfo("security-info"),
        sessionAccept("session-accept"),
        sessionInfo("session-info"),
        sessionInitiate("session-initiate"),
        sessionTerminate("session-terminate"),
        transportAccept("transport-accept"),
        transportInfo("transport-info"),
        transportReject("transport-reject"),
        transportReplace("transport-replace");

        companion object {
            fun fromValue(value: String) = values().find { it.value == value }
        }
    }

    data class MessageInitiationDescription(val xmlns: String, val media: String) {
        companion object {
            fun parse(descEl: Element): MessageInitiationDescription? {
                return descEl.xmlns?.let { xmlns ->
                    descEl.attributes["media"]?.let { media ->
                        MessageInitiationDescription(xmlns, media)
                    }
                };
            }
        }

        fun toElement(): Element {
            return element("description") {
                xmlns = this@MessageInitiationDescription.xmlns;
                attribute("media", this@MessageInitiationDescription.media)
            }
        }
    }

    sealed class MessageInitiationAction(open val id: String, val actionName: String) {

        class Propose(override val id: String, val descriptions: List<MessageInitiationDescription>) :
            MessageInitiationAction(id, "propose")

        class Retract(override val id: String) : MessageInitiationAction(id, "retract")

        class Accept(override val id: String) : MessageInitiationAction(id, "accept")
        class Proceed(override val id: String) : MessageInitiationAction(id, "proceed")
        class Reject(override val id: String) : MessageInitiationAction(id, "reject")

        companion object {
            fun parse(actionEl: Element): MessageInitiationAction? {
                val id = actionEl.attributes["id"] ?: return null;
                when (actionEl.name) {
                    "accept" -> return Accept(id)
                    "proceed" -> return Proceed(id)
                    "propose" -> {
                        val descriptions = actionEl.children.map { MessageInitiationDescription.parse(it) }.filterNotNull();
                        if (descriptions.isNotEmpty()) {
                            return Propose(id, descriptions)
                        } else {
                            return null;
                        }
                    }
                    "retract" -> return Retract(id)
                    "reject" -> return Reject(id)
                    else -> return null;
                }
            }
        }
    }

    enum class TerminateReason(val value: String) {
        alternativeSession("alternative-session"),
        busy("busy"),
        cancel("cancel"),
        connectivityError("connectivity-error"),
        decline("decline"),
        expired("expired"),
        failedApplication("failed-application"),
        failedTransport("failed-transport"),
        generalError("general-error"),
        gone("gone"),
        incompatibleParameters("incompatible-parameters"),
        mediaError("media-error"),
        securityError("security-error"),
        success("success"),
        timeout("timeout"),
        unsupportedApplications("unsupported-applications"),
        unsupportedTransports("unsupported-transports");

        fun toElement(): Element = element(value) {};
        fun toReasonElement(): Element = element("reason") {
            addChild(toElement());
        }

        companion object {
            fun fromValue(value: String) = values().find { it.value == value }
        }
    }

    interface Session {
        enum class State {
            created, initiating, accepted, terminated
        }

        val account: BareJID
        val jid: JID
        val sid: String
        val state: State

        fun terminate(reason: TerminateReason = TerminateReason.success)
    }

    interface SessionManager {
        fun activateSessionSid(account: BareJID, with: JID): String?
    }
}

class JingleModule(
    override val context: Context,
    val sessionManager: Jingle.SessionManager,
    val supportsMessageInitiation: Boolean = true
) : XmppModule {

    companion object {
        const val XMLNS = "urn:xmpp:jingle:1"
        const val MESSAGE_INITIATION_XMLNS = "urn:xmpp:jingle-message:0"
        const val TYPE = XMLNS
        val JMI_CRITERIA = Criterion.chain(Criterion.name(Message.NAME), Criterion.xmlns(MESSAGE_INITIATION_XMLNS));
        val IQ_CRITERIA = Criterion.chain(Criterion.name(IQ.NAME), Criterion.nameAndXmlns("jingle", XMLNS));
        val SUPPORTED_FEATURES = arrayOf(XMLNS) + Description.supportedFeatures + Transport.supportedFeatures;
    }

    override val criteria: Criteria? = if (supportsMessageInitiation) {
        Criterion.or(IQ_CRITERIA, JMI_CRITERIA)
    } else {
        IQ_CRITERIA
    };
    override val type = TYPE
    override val features: Array<String>?
        get() = if (supportsMessageInitiation) {
            SUPPORTED_FEATURES + MESSAGE_INITIATION_XMLNS
        } else {
            SUPPORTED_FEATURES
        }

    override fun initialize() {
        TODO("Not yet implemented")
    }

    override fun process(element: Element) {
        when (element.name) {
            IQ.NAME -> processIq(element);
            Message.NAME -> processMessage(element);
            else -> throw XMPPException(ErrorCondition.FeatureNotImplemented)
        }
    }

    private fun processIq(iq: Element) {
        if (iq.attributes["type"] != "set") {
            throw XMPPException(ErrorCondition.FeatureNotImplemented);
        }

        val jingle = iq.getChildrenNS("jingle", XMLNS) ?: throw XMPPException(ErrorCondition.BadRequest);
        val action = Jingle.Action.fromValue(iq.attributes["action"] ?: throw XMPPException(ErrorCondition.BadRequest))
            ?: throw XMPPException(ErrorCondition.BadRequest);
        val from = JID.parse(iq.attributes["from"] ?: throw XMPPException(ErrorCondition.BadRequest));
        val sid = (jingle.attributes["sid"] ?: sessionManager.activateSessionSid(context.config.userJID!!, from))
            ?: throw XMPPException(ErrorCondition.BadRequest);

        val initiator = jingle.attributes["initiator"]?.let { JID.parse(it) } ?: from;

        val contents = jingle.children.map { Content.parse(it) }.filterNotNull();
        val bundle =
            jingle.getChildrenNS("group", "urn:xmpp:jingle:apps:grouping:0")?.children?.filter { it.name == "content" }
                ?.map { it.attributes["name"] }?.filterNotNull();

        context.eventBus.fire(JingleEvent(from, action, initiator, sid, contents, bundle))
    }

    private fun processMessage(message: Element) {
        val from = message.attributes["from"]?.let { JID.parse(it) } ?: return;
        val action =
            Jingle.MessageInitiationAction.parse(message.children.filter { "urn:xmpp:jingle-message:0".equals(it.xmlns) }
                .firstOrNull() ?: throw XMPPException(ErrorCondition.BadRequest)) ?: return;
        when (action) {
            is Jingle.MessageInitiationAction.Propose -> {
                if (action.descriptions.filter { features?.contains(it.xmlns) == true }.isEmpty()) {
                    this.sendMessageInitiation(Jingle.MessageInitiationAction.Reject(action.id), from);
                    return;
                }
            }
            is Jingle.MessageInitiationAction.Accept -> {
                if (context.modules.getModule<BindModule>(BindModule.TYPE).boundJID?.equals(from) == true) {
                    return;
                }
            }
            else -> {}
        }
        context.eventBus.fire(JingleMessageInitiationEvent(from, action));
    }

    fun sendMessageInitiation(action: Jingle.MessageInitiationAction, jid: JID) {
        when (action) {
            is Jingle.MessageInitiationAction.Proceed -> sendMessageInitiation(Jingle.MessageInitiationAction.Accept(action.id), JID(context.config.userJID!!,null));
            is Jingle.MessageInitiationAction.Reject -> {
                if (jid.bareJID != context.config.userJID) {
                    sendMessageInitiation(Jingle.MessageInitiationAction.Accept(action.id), JID(context.config.userJID!!, null));
                }
            }
            else -> {}
        }

        context.request.message {
            addChild(element(action.actionName) {
                xmlns = "urn:xmpp:jingle-message:0"
                attribute("id", action.id)
                when (action) {
                    is Jingle.MessageInitiationAction.Propose -> action.descriptions.map { it.toElement() }.forEach { addChild(it) }
                }
            })
            type = MessageType.Chat;
            to = jid
        }.send();
    }

    fun initiateSession(jid: JID, sid: String, contents: List<Content>, bundle: List<String>?): IQRequestBuilder<Unit> {
        return context.request.iq {
            to = jid
            type = IQType.Set

            addChild(element("jingle") {
                xmlns = XMLNS
                attribute("action", Jingle.Action.sessionInitiate.value)
                attribute("sid", sid)
                attribute("initiator", context.modules.getModule<BindModule>(BindModule.TYPE).boundJID?.toString() ?: throw XMPPException(ErrorCondition.NotAuthorized))

                contents.map { it.toElement() }.forEach { contentEl -> addChild(contentEl) }
                bundle?.let { bundle ->
                    addChild(element("group") {
                        xmlns = "urn:xmpp:jingle:apps:grouping:0"
                        attribute("semantics", "BUNDLE")
                        bundle.forEach { name ->
                            addChild(element("content") {
                                attribute("name", name)
                            })
                        }
                    })
                }
            })
        }
    }

    fun acceptSession(jid: JID, sid: String, contents: List<Content>, bundle: List<String>?): IQRequestBuilder<Unit> {
        return context.request.iq {
            to = jid
            type = IQType.Set

            addChild(element("jingle") {
                xmlns = XMLNS
                attribute("action", Jingle.Action.sessionAccept.value)
                attribute("sid", sid)
                attribute("responder", context.modules.getModule<BindModule>(BindModule.TYPE).boundJID?.toString() ?: throw XMPPException(ErrorCondition.NotAuthorized))

                contents.map { it.toElement() }.forEach { contentEl -> addChild(contentEl) }
                bundle?.let { bundle ->
                    addChild(element("group") {
                        xmlns = "urn:xmpp:jingle:apps:grouping:0"
                        attribute("semantics", "BUNDLE")
                        bundle.forEach { name ->
                            addChild(element("content") {
                                attribute("name", name)
                            })
                        }
                    })
                }
            })
        }
    }

    fun terminateSession(jid: JID, sid: String, reason: Jingle.TerminateReason): IQRequestBuilder<Unit> {
        return context.request.iq {
            to = jid
            type = IQType.Set

            addChild(element("jingle") {
                xmlns = XMLNS
                attribute("action", Jingle.Action.sessionTerminate.value)
                attribute("sid", sid)
                addChild(reason.toReasonElement())
            })
        }
    }

    fun transportInfo(jid: JID, sid: String, contents: List<Content>): IQRequestBuilder<Unit> {
        return context.request.iq {
            to = jid
            type = IQType.Set

            addChild(element("jingle") {
                xmlns = XMLNS
                attribute("action", Jingle.Action.sessionAccept.value)
                attribute("sid", sid)

                contents.map { it.toElement() }.forEach { contentEl -> addChild(contentEl) }
            })
        }
    }
}

data class JingleEvent(
    val jid: JID,
    val action: Jingle.Action,
    val intiator: JID,
    val sid: String,
    val contents: List<Content>,
    val bundle: List<String>?
) : Event(TYPE) {

    companion object {
        const val TYPE = "tigase.halcyon.core.xmpp.modules.jingle.JingleEvent"
    }

}

data class JingleMessageInitiationEvent(val jid: JID, val action: Jingle.MessageInitiationAction) : Event(TYPE) {
    companion object {
        const val TYPE = "tigase.halcyon.core.xmpp.modules.jingle.JingleMessageInitiationEvent"
    }
}