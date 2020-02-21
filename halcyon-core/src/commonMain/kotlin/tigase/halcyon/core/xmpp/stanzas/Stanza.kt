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
import tigase.halcyon.core.xml.setAtt
import tigase.halcyon.core.xmpp.JID

abstract class Stanza<STANZA_TYPE> protected constructor(protected val element: Element) : Element by element {

	private fun getJID(attName: String): JID? {
		val att = element.attributes[attName]
		return if (att == null) null else JID.parse(att)
	}

	var to: JID?
		get() = getJID("to")
		set(value) = setAtt("to", value?.toString())

	var from: JID?
		get() = getJID("from")
		set(value) = setAtt("from", value?.toString())

	var id: String?
		get() = attributes["id"]
		set(value) = setAtt("id", value)

	abstract var type: STANZA_TYPE

	override fun equals(other: Any?): Boolean {
		return element.equals(other)
	}

	override fun hashCode(): Int {
		return element.hashCode()
	}

	override fun toString(): String {
		return buildString {
			append(name.toUpperCase()).append("[")
			attributes["type"]?.let { append("type=").append(it).append(" ") }
			attributes["id"]?.let { append("id=").append(it).append(" ") }
			attributes["to"]?.let { append("to=").append(it).append(" ") }
			attributes["from"]?.let { append("from=").append(it).append(" ") }
			append("]")
		}
	}

}