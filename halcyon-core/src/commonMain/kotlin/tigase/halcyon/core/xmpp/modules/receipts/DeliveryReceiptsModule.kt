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
package tigase.halcyon.core.xmpp.modules.receipts

import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.HasInterceptors
import tigase.halcyon.core.modules.StanzaInterceptor
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.MessageType
import tigase.halcyon.core.xmpp.stanzas.message
import tigase.halcyon.core.xmpp.toJID

data class MessageDeliveryReceiptEvent(val jid: JID, val msgId: String) : Event(TYPE) { companion object {

	const val TYPE = "tigase.halcyon.core.xmpp.modules.receipts.MessageDeliveryReceiptEvent"
}
}

class DeliveryReceiptsModule(override val context: Context) : XmppModule, HasInterceptors, StanzaInterceptor {

	companion object {

		const val XMLNS = "urn:xmpp:receipts"
		const val TYPE = XMLNS
	}

	override val criteria: Criteria? = null
	override val features: Array<String> = arrayOf(XMLNS)
	override val type = TYPE
	override val stanzaInterceptors: Array<StanzaInterceptor> = arrayOf(this)

	override fun initialize() {
	}

	override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

	override fun afterReceive(element: Element): Element? {
		if (element.name != Message.NAME) return element
		if (element.attributes["type"] == MessageType.Error.value) return element
		val from = element.attributes["from"]?.toJID() ?: return element

		element.getReceiptReceivedID()?.let { id -> context.eventBus.fire(MessageDeliveryReceiptEvent(from, id)) }

		element.getChildrenNS("request", XMLNS)?.let {
			element.attributes["id"]?.let { id ->
				val resp = message {
					element.attributes["from"]?.let {
						attribute("to", it)
					}
					element.attributes["type"]?.let {
						attribute("type", it)
					}
					"received"{
						xmlns = XMLNS
						attribute("id", id)
					}
				}
				context.writer.writeDirectly(resp)
			}
		}


		return element
	}

	override fun beforeSend(element: Element): Element {
		if (element.name != Message.NAME) return element
		if (element.attributes["type"] == MessageType.Groupchat.value) return element
		if (element.attributes["id"] == null) return element
		if (element.getChildrenNS("request", XMLNS) != null) return element
		if (element.getChildrenNS("received", XMLNS) != null) return element
		if (element.getFirstChild("body") == null) return element

		element.add(element("request") {
			xmlns = XMLNS
		})
		return element
	}

}

fun Element.getReceiptReceivedID(): String? {
	return this.getChildrenNS("received", DeliveryReceiptsModule.XMLNS)?.let { it.attributes["id"] }
}