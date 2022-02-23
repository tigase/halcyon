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
package tigase.halcyon.core.xmpp.modules.jingle

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import kotlin.jvm.JvmStatic

class SSRC(val ssrc: String, val parameters: List<Parameter>) {

	fun toElement(): Element {
		return element("source") {
			xmlns = "urn:xmpp:jingle:apps:rtp:ssma:0"
			attribute("ssrc", ssrc)
			attribute("id", ssrc)
			parameters.forEach {
				addChild(it.toElement())
			}
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): SSRC? {
			if ("source".equals(el.name) && "urn:xmpp:jingle:apps:rtp:ssma:0".equals(el.xmlns)) {
				val ssrc = el.attributes["ssrc"] ?: el.attributes["id"] ?: return null
				val parameters = el.children.map { Parameter.parse(it) }.filterNotNull()
				return SSRC(ssrc, parameters)
			}
			return null
		}
	}

	class Parameter(val name: String, val value: String?) {

		fun toElement(): Element {
			return element("parameter") {
				attribute("name", name)
				this@Parameter.value?.let { attribute("value", it) }
			}
		}

		companion object {

			@JvmStatic
			fun parse(el: Element): Parameter? {
				if ("parameter".equals(el.name)) {
					val name = el.attributes["name"] ?: return null
					val value = el.attributes["value"]
					return Parameter(name, value)
				}
				return null
			}
		}
	}

}