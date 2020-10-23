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
package tigase.halcyon.core.xmpp.modules.chatstates

import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.MessageReceivedEvent
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.message

enum class ChatState(val xmppValue: String) { /**
 * User is actively participating in the chat session.
 */
Active("active"),

	/**
	 * User has not been actively participating in the chat session.
	 */
	Inactive("inactive"),

	/**
	 * User has effectively ended their participation in the chat session.
	 */
	Gone("gone"),

	/**
	 * User is composing a message.
	 */
	Composing("composing"),

	/**
	 * User had been composing but now has stopped.
	 */
	Paused("paused"),
}

private fun findChatState(element: Element): ChatState? {
	val csi = element.children.find {
		it.xmlns == ChatStateModule.XMLNS
	} ?: return null
	return ChatState.values().find { chatState -> chatState.xmppValue == csi.name } ?: throw XMPPException(
		ErrorCondition.BadRequest, "Unknown chat state ${csi.name}"
	)
}

private fun setChatState(element: Element, state: ChatState?) {
	element.children.find { it.xmlns == ChatStateModule.XMLNS }?.let { element.remove(it) }
	state?.let {
		element.add(element(state.xmppValue) {
			xmlns = ChatStateModule.XMLNS
		})
	}
}

var Message.chatState: ChatState?
	get() = findChatState(this)
	set(value) = setChatState(this, value)

data class ChatStateEvent(val jid: JID, val state: ChatState) : Event(TYPE) { companion object {

	const val TYPE = "tigase.halcyon.core.xmpp.modules.chatstates.ChatStateEvent"
}
}

class ChatStateModule(override val context: Context) : XmppModule {
//	private val log LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.chatstates.ChatStateModule")

	override val type = TYPE
	override val criteria: Criteria? = null
	override val features: Array<String>? = arrayOf(XMLNS)

	companion object {

		const val XMLNS = "http://jabber.org/protocol/chatstates"
		const val TYPE = XMLNS
	}

	override fun initialize() {
		context.eventBus.register<MessageReceivedEvent>(MessageReceivedEvent.TYPE) { event ->
			findChatState(event.stanza)?.let { state ->
				if (event.fromJID != null) context.eventBus.fire(ChatStateEvent(event.fromJID, state))
			}
		}
		context.eventBus.register<OwnChatStateChangeEvent>(OwnChatStateChangeEvent.TYPE) { event ->
			if (event.sendUpdate) {
				publishChatState(event.jid, event.state)
			}
		}
	}

	fun publishChatState(jid: BareJID, state: ChatState) {
		var msg = message {
			to = jid.toJID()
			state.xmppValue {
				xmlns = XMLNS
			}
			"no-store"{
				xmlns = "urn:xmpp:hints"
			}
		}
		context.request.message(msg).send()
	}

	override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

}