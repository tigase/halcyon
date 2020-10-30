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
package tigase.halcyon.core.xmpp.modules.carbons

import getFromAttr
import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.MessageModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.asStanza

sealed class CarbonEvent(val fromJID: JID?, val stanza: Message) : Event(TYPE) { companion object {

	const val TYPE = "tigase.halcyon.core.xmpp.modules.carbons.CarbonEvent"
}

	class Sent(fromJID: JID?, stanza: Message) : CarbonEvent(fromJID, stanza)
	class Received(fromJID: JID?, stanza: Message) : CarbonEvent(fromJID, stanza)
}

class MessageCarbonsModule(override val context: Context, private val forwardHandler: (Message) -> Unit) : XmppModule {

	private var messageModule: MessageModule? = null
	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.carbons.MessageCarbonsModule")

	override val type = TYPE
	override val criteria = Criterion.chain(Criterion.name(Message.NAME), Criterion.xmlns(XMLNS))
	override val features: Array<String>? = arrayOf(XMLNS)

	companion object {

		const val XMLNS = "urn:xmpp:carbons:2"
		const val TYPE = XMLNS
		private const val FORWARD_XMLNS = "urn:xmpp:forward:0"
	}

	override fun initialize() {
		this.messageModule = context.modules.getModuleOrNull(MessageModule.TYPE)
	}

	override fun process(element: Element) {
		val ownJid = context.modules.getModuleOrNull<BindModule>(BindModule.TYPE)?.boundJID?.bareJID
		val from = element.getFromAttr()
		if (from != null && from.bareJID != ownJid) throw XMPPException(ErrorCondition.NotAcceptable)
		element.getChildrenNS(XMLNS).firstOrNull()?.let {
			when (it.name) {
				"sent" -> processSent(element, it)
				"received" -> processReceived(element, it)
				else -> throw XMPPException(ErrorCondition.BadRequest)
			}
		}
	}

	fun enable(): RequestBuilder<Unit, IQ> = context.request.iq {
		type = IQType.Set
		"enable"{
			xmlns = XMLNS
		}
	}.map { Unit }

	fun disable(): RequestBuilder<Unit, IQ> = context.request.iq {
		type = IQType.Set
		"disable"{
			xmlns = XMLNS
		}
	}.map { Unit }

	private fun processSent(message: Element, carbon: Element) {
		val msg = carbon.getChildrenNS("forwarded", FORWARD_XMLNS)?.getChildren(Message.NAME)?.firstOrNull()
			?.asStanza<Message>() ?: return

		messageModule?.process(msg)
//		forwardHandler?.invoke(msg)
		context.eventBus.fire(CarbonEvent.Sent(msg.from, msg))
	}

	private fun processReceived(message: Element, carbon: Element) {
		val msg = carbon.getChildrenNS("forwarded", FORWARD_XMLNS)?.getChildren(Message.NAME)?.firstOrNull()
			?.asStanza<Message>() ?: return

		forwardHandler.invoke(msg)
		context.eventBus.fire(CarbonEvent.Received(msg.from, msg))
	}

}
