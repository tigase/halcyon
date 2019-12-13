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

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

enum class MessageType(val value: String) {
	Chat("chat"),
	Error("error"),
	Groupchat("groupchat"),
	Headline("headline"),
	Normal("normal")
}

class Message(wrappedElement: Element) : Stanza<MessageType?>(wrappedElement) {
	companion object {
		const val NAME = "message"
	}

	override var type: MessageType?
		set(value) = setAtt("type", value?.value)
		get() = attributes["type"]?.let {
			MessageType.values().firstOrNull { te -> te.value == it }
				?: throw XMPPException(ErrorCondition.BadRequest, "Unknown stanza type '$it'")
		}
}
