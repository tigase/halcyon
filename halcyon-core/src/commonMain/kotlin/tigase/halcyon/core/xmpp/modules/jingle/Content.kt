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

class Content(
	val creator: Creator, val name: String, val description: Description?, val transports: List<Transport>
) {

	enum class Creator { initiator,
		responder
	}

	fun toElement(): Element {
		return element("content") {
			attribute("name", name)
			attribute("creator", creator.name)
			description?.let { addChild(it.toElement()) }
			transports.forEach { addChild(it.toElement()) }
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): Content? {
			if ("content" == el.name) {
				val name = el.attributes["name"] ?: return null
				val creator = el.attributes["creator"]?.let { Creator.valueOf(it) } ?: return null
				val description = el.getFirstChild("description")?.let { Description.parse(it) }
				val transports = el.children.map { Transport.parse(it) }.filterNotNull()
				return Content(creator, name, description, transports)
			}
			return null
		}
	}
}