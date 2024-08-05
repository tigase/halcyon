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
package tigase.halcyon.core.xmpp.modules.jingle

import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.ElementBuilder
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.MessageType

class Jingle {

	enum class ContentAction {
		Add,
		Accept,
		Remove,
		Modify;

		companion object {
			fun from(action: Action): ContentAction? = when (action) {
				Action.ContentAdd -> Add
				Action.ContentAccept -> Accept
				Action.ContentRemove -> Remove
				Action.ContentModify -> Modify
				else -> null;
			}
		}

		fun jingleAction(): Action = when (this) {
			Add -> Action.ContentAdd
			Accept -> Action.ContentAccept
			Remove -> Action.ContentRemove
			Modify -> Action.ContentModify
		}
	}

	interface Session {

		enum class State {

			Created, Initiating, Accepted, Terminated
		}

		val account: BareJID
		val jid: JID
		val sid: String
		val state: State

		fun terminate(reason: TerminateReason = TerminateReason.Success)
	}

	interface SessionManager {

		fun sessionInitiated(context: Context, jid: JID, sid: String, contents: List<Content>, bundle: List<String>?)

		@Throws(XMPPException::class)
		fun sessionAccepted(context: Context, jid: JID, sid: String, contents: List<Content>, bundle: List<String>?)

		fun sessionTerminated(context: Context, jid: JID, sid: String)

		@Throws(XMPPException::class)
		fun transportInfo(context: Context, jid: JID, sid: String, contents: List<Content>)

		fun messageInitiation(context: Context, fromJid: JID, action: MessageInitiationAction)

		fun contentModified(context: Context, jid: JID, sid: String, action: ContentAction, contents: List<Content>, bundle: List<String>?)

		fun sessionInfo(context: Context, jid: JID, sid: String, info: List<SessionInfo>)
	}

	sealed class SessionInfo {
		class Active(): SessionInfo()
		class Hold(): SessionInfo()
		class Unhold(): SessionInfo()
		class Mute(val contentName: String?): SessionInfo()
		class Unmute(val contentName: String?): SessionInfo()
		class Ringing(): SessionInfo()

		companion object {
			val XMLNS = "urn:xmpp:jingle:apps:rtp:info:1";
			fun parse(el: Element): SessionInfo? {
				if (!XMLNS.equals(el.xmlns)) return null;
				return when (el.name) {
					"active" -> Active()
					"hold" -> Hold()
					"unhold" -> Unhold()
					"mute" -> Mute(el.attributes.get("name"))
					"unmute" -> Unmute(el.attributes.get("name"))
					"ringing" -> Ringing()
					else -> null
				}
			}
		}

		val name: String = when (this) {
			is Active -> "active"
			is Hold -> "hold"
			is Unhold -> "unhold"
			is Mute -> "mute"
			is Unmute -> "unmute"
			is Ringing -> "ringing"
		}

		fun element(creatorProvider: (String)->Content.Creator): Element {
			val el = ElementBuilder.create(name, XMLNS);
			when (this) {
				is Mute -> {
					this.contentName?.let {
						el.attribute("creator", creatorProvider(it).name)
						el.attribute("name", it)
					}
				}
				is Unmute -> {
					this.contentName?.let {
						el.attribute("creator", creatorProvider(it).name)
						el.attribute("name", it)
					}
				}
				else -> {}
			}
			return el.build();
		}
	}

}

@HalcyonConfigDsl
interface JingleModuleConfig

class JingleModule(
	override val context: Context,
	val sessionManager: Jingle.SessionManager,
	val supportsMessageInitiation: Boolean = true,
) : XmppModule, JingleModuleConfig {

	companion object {

		const val XMLNS = "urn:xmpp:jingle:1"
		const val MESSAGE_INITIATION_XMLNS = "urn:xmpp:jingle-message:0"
		const val TYPE = XMLNS
		val JMI_CRITERIA = Criterion.chain(Criterion.name(Message.NAME), Criterion.xmlns(MESSAGE_INITIATION_XMLNS))
		val IQ_CRITERIA = Criterion.chain(Criterion.name(IQ.NAME), Criterion.nameAndXmlns("jingle", XMLNS))
		val SUPPORTED_FEATURES = arrayOf(XMLNS) + Description.supportedFeatures + Transport.supportedFeatures
	}

	override val criteria: Criteria = if (supportsMessageInitiation) {
		Criterion.or(IQ_CRITERIA, JMI_CRITERIA)
	} else {
		IQ_CRITERIA
	}
	override val type = TYPE
	override val features: Array<String>
		get() = if (supportsMessageInitiation) {
			SUPPORTED_FEATURES + MESSAGE_INITIATION_XMLNS
		} else {
			SUPPORTED_FEATURES
		}

	override fun process(element: Element) {
		when (element.name) {
			IQ.NAME -> processIq(element)
			Message.NAME -> processMessage(element)
			else -> throw XMPPException(ErrorCondition.FeatureNotImplemented)
		}
	}

	private fun processIq(iq: Element) {
		if (iq.attributes["type"] != "set") {
			throw XMPPException(ErrorCondition.FeatureNotImplemented, "All messages should be of type 'set'")
		}
		val id = iq.attributes["id"];

		val jingle = iq.getChildrenNS("jingle", XMLNS) ?: throw XMPPException(ErrorCondition.BadRequest, "Missing 'jingle' element")
		val action = Action.fromValue(jingle.attributes["action"] ?: "") ?: throw XMPPException(
			ErrorCondition.BadRequest,
			"Missing or invalid action attribute"
		)

		val sid = jingle.attributes["sid"] ?: throw XMPPException(ErrorCondition.BadRequest, "Missing sid attribute");

		val from = iq.attributes["from"]?.toJID() ?: throw XMPPException(ErrorCondition.BadRequest, "Missing 'from' attribute")
		val initiator = (jingle.attributes["initiator"]?.toJID() ?: from) ?: throw XMPPException(ErrorCondition.BadRequest, "Missing 'initiator' attribute");

		val contents = jingle.children.map { Content.parse(it) }.filterNotNull()
		val bundle = jingle.getChildrenNS("group", "urn:xmpp:jingle:apps:grouping:0")?.let(Bundle::parse)

		context.eventBus.fire(JingleEvent(from, action, initiator, sid, contents, bundle))

		when (action) {
			Action.SessionInitiate -> sessionManager.sessionInitiated(this.context, from, sid, contents, bundle);
			Action.SessionAccept -> sessionManager.sessionAccepted(this.context, from, sid, contents, bundle);
			Action.SessionTerminate -> sessionManager.sessionTerminated(this.context, from, sid)
			Action.TransportInfo -> sessionManager.transportInfo(this.context, from, sid, contents);
			Action.ContentAccept, Action.ContentModify, Action.ContentRemove -> {
				val contentAction = Jingle.ContentAction.from(action) ?: throw XMPPException(ErrorCondition.BadRequest, "Invalid action");
				sessionManager.contentModified(context, from, sid, contentAction, contents, bundle);
			}
			Action.SessionInfo -> {
				val infos = jingle.getChildrenNS(Jingle.SessionInfo.XMLNS).map(Jingle.SessionInfo::parse).filterNotNull();
				sessionManager.sessionInfo(context, from, sid, infos);
			}
			else -> throw XMPPException(ErrorCondition.FeatureNotImplemented);
		}
		context.request.iq {
			to = from
			id(id)
			type = IQType.Result
		}.send()
	}

	private fun processMessage(message: Element) {
		val from = message.attributes["from"]?.toJID() ?: return
		val action =
			MessageInitiationAction.parse(message.children.filter { "urn:xmpp:jingle-message:0".equals(it.xmlns) }
											  .firstOrNull() ?: throw XMPPException(ErrorCondition.BadRequest))
				?: return
		when (action) {
			is MessageInitiationAction.Propose -> {
				if (action.descriptions.none { features.contains(it.xmlns) }) {
					this.sendMessageInitiation(MessageInitiationAction.Reject(action.id), from).send()
					return
				}
			}

			is MessageInitiationAction.Accept -> {
				if (context.boundJID?.equals(from) == true) {
					return
				}
			}

			else -> {
			}
		}
		context.eventBus.fire(JingleMessageInitiationEvent(from, action))
		sessionManager.messageInitiation(context, from, action);
	}

	fun sendMessageInitiation(
		action: MessageInitiationAction, jid: JID,
	): RequestBuilder<Unit, Message> {
		when (action) {
			is MessageInitiationAction.Proceed -> sendMessageInitiation(
				MessageInitiationAction.Accept(action.id), context.boundJID!!.bareJID
			).send()

			is MessageInitiationAction.Reject -> {
				if (jid.bareJID != context.boundJID?.bareJID) {
					sendMessageInitiation(
						MessageInitiationAction.Reject(action.id), context.boundJID!!.bareJID
					).send()
				}
			}

			else -> {
			}
		}

		return context.request.message {
			element(action.actionName) {
				xmlns = "urn:xmpp:jingle-message:0"
				attribute("id", action.id)
				if (action is MessageInitiationAction.Propose) action.descriptions.map { it.toElement() }
					.forEach { addChild(it) }
			}
			type = MessageType.Chat
			to = jid
		}
	}

	fun initiateSession(
		jid: JID, sid: String, contents: List<Content>, bundle: List<String>?,
	): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			to = jid
			type = IQType.Set

			element("jingle") {
				xmlns = XMLNS
				attribute("action", Action.SessionInitiate.value)
				attribute("sid", sid)
				attribute(
					"initiator", context.boundJID?.toString() ?: throw XMPPException(ErrorCondition.NotAuthorized)
				)

				contents.map { it.toElement() }.forEach { contentEl -> addChild(contentEl) }
				Bundle.toElement(bundle, ::addChild)
			}
		}.map { }
	}

	fun acceptSession(
		jid: JID, sid: String, contents: List<Content>, bundle: List<String>?,
	): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			to = jid
			type = IQType.Set

			element("jingle") {
				xmlns = XMLNS
				attribute("action", Action.SessionAccept.value)
				attribute("sid", sid)
				attribute(
					"responder", context.boundJID?.toString() ?: throw XMPPException(ErrorCondition.NotAuthorized)
				)

				contents.map { it.toElement() }.forEach { contentEl -> addChild(contentEl) }
				Bundle.toElement(bundle, ::addChild)
			}
		}.map { }
	}

	fun sessionInfo(jid: JID, sid: String, actions: List<Jingle.SessionInfo>, creatorProvider: (String) -> Content.Creator): RequestBuilder<Unit,IQ> {
		return context.request.iq {
			to = jid
			type = IQType.Set

			element("jingle") {
				xmlns = XMLNS
				attribute("action", Action.SessionInfo.value)
				attribute("sid", sid)

				actions.map { it.element(creatorProvider) }.forEach { this.addChild(it) }
			}
		}.map {}
	}

	fun terminateSession(jid: JID, sid: String, reason: TerminateReason): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			to = jid
			type = IQType.Set

			element("jingle") {
				xmlns = XMLNS
				attribute("action", Action.SessionTerminate.value)
				attribute("sid", sid)
				addChild(reason.toReasonElement())
			}
		}.map { }
	}

	fun transportInfo(jid: JID, sid: String, contents: List<Content>): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			to = jid
			type = IQType.Set

			element("jingle") {
				xmlns = XMLNS
				attribute("action", Action.TransportInfo.value)
				attribute("sid", sid)

				contents.map { it.toElement() }.forEach { contentEl -> addChild(contentEl) }
			}
		}.map { }
	}

	fun contentModify(jid: JID, sid: String, action: Jingle.ContentAction, contents: List<Content>, bundle: List<String>?): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			to = jid
			type = IQType.Set

			element("jingle") {
				xmlns = XMLNS
				attribute("action", action.jingleAction().value)
				attribute("sid", sid)

				contents.map { it.toElement() }.forEach { contentEl -> addChild(contentEl) }

				Bundle.toElement(bundle, ::addChild)
			}
		}.map { }
	}
}

data class JingleEvent(
	val jid: JID,
	val action: Action,
	val intiator: JID,
	val sid: String,
	val contents: List<Content>,
	val bundle: List<String>?,
) : Event(TYPE) {

	companion object : EventDefinition<JingleEvent> {

		override val TYPE = "tigase.halcyon.core.xmpp.modules.jingle.JingleEvent"
	}

}

data class JingleMessageInitiationEvent(val jid: JID, val action: MessageInitiationAction) : Event(TYPE) {

	companion object : EventDefinition<JingleMessageInitiationEvent> {

		override val TYPE = "tigase.halcyon.core.xmpp.modules.jingle.JingleMessageInitiationEvent"
	}
}