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
package tigase.halcyon.core.xmpp.modules.pubsub

import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.wrap

data class PubSubEventReceivedEvent(
	val fromJID: JID?, val stanza: Message, val nodeName: String, val items: List<Element>
) : Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.pubsub.PubSubEventReceivedEvent"
	}
}

class PubSubModule : XmppModule {

	private val log = Logger("tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule")

	override val type = TYPE
	override lateinit var context: Context
	override val criteria = Criterion.chain(
		Criterion.name(Message.NAME), Criterion.nameAndXmlns(
			"event", XMLNS_EVENT
		)
	)
	override val features: Array<String>? = null

	companion object {
		const val XMLNS = "http://jabber.org/protocol/pubsub"
		const val TYPE = XMLNS
		const val XMLNS_EVENT = "$XMLNS#event"

	}

	override fun initialize() {
	}

	override fun process(element: Element) {
		val msg: Message = wrap(element)
		val eventElement = msg.getChildrenNS(
			"event", XMLNS_EVENT
		)!!
		val itemsElement = eventElement.getFirstChild("items")!!
		val nodeName = itemsElement.attributes["node"]!!
		val itemsList = itemsElement.getChildren("item")

		context.eventBus.fire(
			PubSubEventReceivedEvent(
				msg.from, msg, nodeName, itemsList
			)
		)
	}

}

