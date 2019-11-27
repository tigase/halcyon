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

import getTypeAttr
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.ElementImpl
import tigase.halcyon.core.xml.ElementNode
import tigase.halcyon.core.xmpp.IdGenerator
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.StanzaType

open class StanzaNode(element: Element) : ElementNode(element) {

	private fun getJID(attName: String): JID? {
		val att = element.attributes[attName]
		return if (att == null) null else JID.parse(att)
	}

	private fun setAtt(attName: String, value: String?) {
		if (value == null) {
			element.attributes.remove(attName)
		} else {
			element.attributes[attName] = value
		}
	}

	fun id() {
		element.attributes["id"] = IdGenerator.nextId()
	}

	var to: JID?
		get() = getJID("to")
		set(value) = setAtt("to", value?.toString())

	var from: JID?
		get() = getJID("from")
		set(value) = setAtt("from", value?.toString())

	var type: StanzaType?
		set(value) = setAtt("type", value?.name?.toLowerCase())
		get() = element.getTypeAttr()
}

class PresenceNode(element: Presence) : StanzaNode(element)
class IQNode(element: IQ) : StanzaNode(element)
class MessageNode(element: Message) : StanzaNode(element)

@Suppress("UNCHECKED_CAST")
fun <ST : Element> wrap(element: Element): ST {
	return when (element.name) {
		Presence.NAME -> Presence(element) as ST
		IQ.NAME -> IQ(element) as ST
		Message.NAME -> Message(element) as ST
		else -> throw HalcyonException("Unknown stanza type '${element.name}'.")
	}
}

private fun stanzaByName(name: String): Element = when (name) {
	Presence.NAME -> Presence(ElementImpl(name))
	IQ.NAME -> IQ(ElementImpl(name))
	Message.NAME -> Message(ElementImpl(name))
	else -> throw HalcyonException("Unknown stanza type '$name'.")
}

@Suppress("UNCHECKED_CAST")
fun <ST : Element> stanza(name: String, init: StanzaNode.() -> Unit): ST {
	val n = StanzaNode(stanzaByName(name))
	n.init()
	n.id()
	return n.element as ST
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