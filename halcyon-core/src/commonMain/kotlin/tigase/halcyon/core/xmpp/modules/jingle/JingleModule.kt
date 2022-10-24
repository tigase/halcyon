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
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.MessageType

class Jingle {

	interface Session {

		enum class State {

			Created,
			Initiating,
			Accepted,
			Terminated
		}

		val account: BareJID
		val jid: JID
		val sid: String
		val state: State

		fun terminate(reason: TerminateReason = TerminateReason.Success)
	}

	interface SessionManager {

		fun activateSessionSid(account: BareJID, with: JID): String?
	}
}

class JingleModule(
	override val context: Context,
	val sessionManager: Jingle.SessionManager,
	val supportsMessageInitiation: Boolean = true,
) : XmppModule {

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

	override fun initialize() {
		TODO("Not yet implemented")
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
			throw XMPPException(ErrorCondition.FeatureNotImplemented)
		}

		val jingle = iq.getChildrenNS("jingle", XMLNS) ?: throw XMPPException(ErrorCondition.BadRequest)
		val action = Action.fromValue(iq.attributes["action"] ?: throw XMPPException(ErrorCondition.BadRequest))
			?: throw XMPPException(ErrorCondition.BadRequest)
		val from = JID.parse(iq.attributes["from"] ?: throw XMPPException(ErrorCondition.BadRequest))
		val sid =
			(jingle.attributes["sid"] ?: sessionManager.activateSessionSid(context.config.account!!.userJID, from))
				?: throw XMPPException(ErrorCondition.BadRequest)

		val initiator = jingle.attributes["initiator"]?.let { JID.parse(it) } ?: from

		val contents = jingle.children.map { Content.parse(it) }
			.filterNotNull()
		val bundle =
			jingle.getChildrenNS("group", "urn:xmpp:jingle:apps:grouping:0")?.children?.filter { it.name == "content" }
				?.map { it.attributes["name"] }
				?.filterNotNull()

		context.eventBus.fire(JingleEvent(from, action, initiator, sid, contents, bundle))
	}

	private fun processMessage(message: Element) {
		val from = message.attributes["from"]?.let { JID.parse(it) } ?: return
		val action =
			MessageInitiationAction.parse(message.children.filter { "urn:xmpp:jingle-message:0".equals(it.xmlns) }
											  .firstOrNull() ?: throw XMPPException(ErrorCondition.BadRequest))
				?: return
		when (action) {
			is MessageInitiationAction.Propose -> {
				if (action.descriptions.none { features.contains(it.xmlns) }) {
					this.sendMessageInitiation(MessageInitiationAction.Reject(action.id), from)
						.send()
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
	}

	fun sendMessageInitiation(
		action: MessageInitiationAction, jid: JID,
	): RequestBuilder<Unit, Message> {
		when (action) {
			is MessageInitiationAction.Proceed -> sendMessageInitiation(
				MessageInitiationAction.Accept(action.id), JID(context.config.account!!.userJID, null)
			).send()

			is MessageInitiationAction.Reject -> {
				if (jid.bareJID != context.config.account!!.userJID) {
					sendMessageInitiation(
						MessageInitiationAction.Accept(action.id), JID(context.config.account!!.userJID, null)
					).send()
				}
			}

			else -> {
			}
		}

		return context.request.message {
			addChild(element(action.actionName) {
				xmlns = "urn:xmpp:jingle-message:0"
				attribute("id", action.id)
				if (action is MessageInitiationAction.Propose) action.descriptions.map { it.toElement() }
					.forEach { addChild(it) }
			})
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

			addChild(element("jingle") {
				xmlns = XMLNS
				attribute("action", Action.SessionInitiate.value)
				attribute("sid", sid)
				attribute(
					"initiator", context.boundJID?.toString() ?: throw XMPPException(ErrorCondition.NotAuthorized)
				)

				contents.map { it.toElement() }
					.forEach { contentEl -> addChild(contentEl) }
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
			.map { }
	}

	fun acceptSession(
		jid: JID, sid: String, contents: List<Content>, bundle: List<String>?,
	): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			to = jid
			type = IQType.Set

			addChild(element("jingle") {
				xmlns = XMLNS
				attribute("action", Action.SessionAccept.value)
				attribute("sid", sid)
				attribute(
					"responder", context.boundJID?.toString() ?: throw XMPPException(ErrorCondition.NotAuthorized)
				)

				contents.map { it.toElement() }
					.forEach { contentEl -> addChild(contentEl) }
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
			.map { }
	}

	fun terminateSession(jid: JID, sid: String, reason: TerminateReason): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			to = jid
			type = IQType.Set

			addChild(element("jingle") {
				xmlns = XMLNS
				attribute("action", Action.SessionTerminate.value)
				attribute("sid", sid)
				addChild(reason.toReasonElement())
			})
		}
			.map { }
	}

	fun transportInfo(jid: JID, sid: String, contents: List<Content>): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			to = jid
			type = IQType.Set

			addChild(element("jingle") {
				xmlns = XMLNS
				attribute("action", Action.SessionAccept.value)
				attribute("sid", sid)

				contents.map { it.toElement() }
					.forEach { contentEl -> addChild(contentEl) }
			})
		}
			.map { }
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

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.jingle.JingleEvent"
	}

}

data class JingleMessageInitiationEvent(val jid: JID, val action: MessageInitiationAction) : Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.jingle.JingleMessageInitiationEvent"
	}
}