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
package tigase.halcyon.core.xmpp.modules.jingle

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import kotlin.jvm.JvmStatic

class SSRCGroup(val semantics: String, val sources: List<String>) {

	fun toElement(): Element {
		return element("ssrc-group") {
			xmlns = "urn:xmpp:jingle:apps:rtp:ssma:0"
			attribute("semantics", semantics)
			sources.forEach {
				addChild(element("source") {
					attribute("ssrc", it)
				})
			}
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): SSRCGroup? {
			if ("ssrc-group".equals(el.name) && "urn:xmpp:jingle:apps:rtp:ssma:0".equals(el.xmlns)) {
				val semantics = el.attributes["semantics"] ?: return null
				val sources =
					el.children.filter { "source".equals(it.name) }.map { it.attributes["ssrc"] }.filterNotNull()
				return SSRCGroup(semantics, sources)
			}
			return null
		}
	}
}