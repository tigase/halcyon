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
import tigase.halcyon.core.xmpp.JID

fun tigase.halcyon.core.xml.Element.getFromAttr(): JID? {
	val tmp = this.attributes["from"] ?: return null
	return JID.parse(tmp)
}

fun tigase.halcyon.core.xml.Element.getToAttr(): JID? {
	val tmp = this.attributes["to"] ?: return null
	return JID.parse(tmp)
}

fun tigase.halcyon.core.xml.Element.getIdAttr(): String? {
	return this.attributes["id"]
}

fun tigase.halcyon.core.xml.Element.getTypeAttr(): tigase.halcyon.core.xmpp.StanzaType {
	val tmp = this.attributes["type"] ?: return tigase.halcyon.core.xmpp.StanzaType.Normal
	return when (tmp.toLowerCase()) {
		"chat" -> tigase.halcyon.core.xmpp.StanzaType.Chat
		"error" -> tigase.halcyon.core.xmpp.StanzaType.Error
		"get" -> tigase.halcyon.core.xmpp.StanzaType.Get
		"groupchat" -> tigase.halcyon.core.xmpp.StanzaType.GroupChat
		"headline" -> tigase.halcyon.core.xmpp.StanzaType.Headline
		"probe" -> tigase.halcyon.core.xmpp.StanzaType.Probe
		"result" -> tigase.halcyon.core.xmpp.StanzaType.Result
		"set" -> tigase.halcyon.core.xmpp.StanzaType.Set
		"subscribe" -> tigase.halcyon.core.xmpp.StanzaType.Subscribe
		"subscribed" -> tigase.halcyon.core.xmpp.StanzaType.Subscribed
		"unavailable" -> tigase.halcyon.core.xmpp.StanzaType.Unavailable
		"unsubscribe" -> tigase.halcyon.core.xmpp.StanzaType.Unsubscribe
		"unsubscribed" -> tigase.halcyon.core.xmpp.StanzaType.Unsubscribed
		else -> tigase.halcyon.core.xmpp.StanzaType.Normal
	}
}
