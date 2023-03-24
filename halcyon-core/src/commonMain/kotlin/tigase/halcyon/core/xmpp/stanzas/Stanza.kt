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
package tigase.halcyon.core.xmpp.stanzas

import kotlinx.serialization.Serializable
import tigase.halcyon.core.xml.*
import tigase.halcyon.core.xmpp.JID

@Serializable(with = StanzaSerializer::class)
sealed class Stanza<STANZA_TYPE>(protected val element: Element) : Element by element {

	var to: JID? by jidAttributeProperty()
	var from: JID? by jidAttributeProperty()
	var id: String? by stringAttributeProperty()

	abstract var type: STANZA_TYPE

	override fun equals(other: Any?): Boolean {
		return element.equals(other)
	}

	override fun hashCode(): Int {
		return element.hashCode()
	}

	override fun toString(): String {
		return buildString {
			append(name.uppercase()).append("[")
			attributes["type"]?.let {
				append("type=").append(it)
					.append(" ")
			}
			attributes["id"]?.let {
				append("id=").append(it)
					.append(" ")
			}
			attributes["to"]?.let {
				append("to=").append(it)
					.append(" ")
			}
			attributes["from"]?.let {
				append("from=").append(it)
					.append(" ")
			}
			append("]")
		}
	}

}