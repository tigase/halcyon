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
package tigase.halcyon.core.xmpp.stanzas

import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.ElementImpl
import tigase.halcyon.core.xml.ElementNode
import tigase.halcyon.core.xmpp.IdGenerator
import tigase.halcyon.core.xmpp.JID

abstract class StanzaNode<STANZA_TYPE>(element: Element) : ElementNode(element) {

	private fun getJID(attName: String): JID? {
		val att = element.attributes[attName]
		return if (att == null) null else JID.parse(att)
	}

	protected open fun setAtt(attName: String, value: String?) {
		if (value == null) {
			element.attributes.remove(attName)
		} else {
			element.attributes[attName] = value
		}
	}

	fun id() {
		if (!element.attributes.containsKey("id")) element.attributes["id"] = IdGenerator.nextId()
	}

	var to: JID?
		get() = getJID("to")
		set(value) = setAtt("to", value?.toString())

	var from: JID?
		get() = getJID("from")
		set(value) = setAtt("from", value?.toString())

	abstract var type: STANZA_TYPE

}

class PresenceNode(private val presence: Presence) : StanzaNode<PresenceType?>(presence) {
	override var type: PresenceType?
		set(value) = setAtt("type", value?.value)
		get() = PresenceType.values().firstOrNull { te -> te.value == value }

	private fun intSetShow(show: Show?) {
		presence.show = show
	}

	private fun intSetPriority(value: Int) {
		presence.priority = value
	}

	private fun intSetStatus(value: String?) {
		presence.status = value
	}

	var show: Show?
		set(value) = intSetShow(value)
		get() = presence.show

	var priority: Int
		set(value) = intSetPriority(value)
		get() = presence.priority

	var status: String?
		set(value) = intSetStatus(value)
		get() = presence.status

}

class IQNode(element: IQ) : StanzaNode<IQType>(element) {
	override var type: IQType
		set(value) = setAtt("type", value.value)
		get() = IQType.values().first { te -> te.value == value }
}

class MessageNode(element: Message) : StanzaNode<MessageType?>(element) {
	override var type: MessageType?
		set(value) = setAtt("type", value?.value)
		get() = MessageType.values().firstOrNull { te -> te.value == value }
}

@Suppress("UNCHECKED_CAST")
fun < ST : Stanza<*>> wrap(element: Element): ST {
	return if (element is Stanza<*>) element as ST
	else when (element.name) {
		Presence.NAME -> Presence(element) as ST
		IQ.NAME -> IQ(element) as ST
		Message.NAME -> Message(element) as ST
		else -> throw HalcyonException("Unknown stanza type '${element.name}'.")
	}
}

fun presence(init: PresenceNode.() -> Unit): Presence {
	val n = PresenceNode(Presence(ElementImpl(Presence.NAME)))
	n.init()
	n.id()
	return n.element as Presence
}

fun message(init: MessageNode.() -> Unit): Message {
	val n = MessageNode(Message(ElementImpl(Message.NAME)))
	n.init()
	n.id()
	return n.element as Message
}

fun iq(init: IQNode.() -> Unit): IQ {
	val n = IQNode(IQ(ElementImpl(IQ.NAME)))
	n.init()
	n.id()
	return n.element as IQ
}