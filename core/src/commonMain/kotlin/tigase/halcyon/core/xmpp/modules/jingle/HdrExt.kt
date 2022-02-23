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

class HdrExt(val id: String, val uri: String, val senders: Description.Senders) {

	fun toElement(): Element {
		return element("rtp-hdrext") {
			xmlns = "urn:xmpp:jingle:apps:rtp:rtp-hdrext:0"
			attribute("id", id)
			attribute("uri", uri)
			when (senders) {
				Description.Senders.Both -> {
				}
				else -> attribute("senders", senders.name)
			}
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): HdrExt? {
			if ("rtp-hdrext".equals(el.name) && "urn:xmpp:jingle:apps:rtp:rtp-hdrext:0".equals(el.xmlns)) {
				val id = el.attributes["id"] ?: return null
				val uri = el.attributes["uri"] ?: return null
				val senders =
					el.attributes["senders"]?.let { Description.Senders.valueOf(it) } ?: Description.Senders.Both
				return HdrExt(id, uri, senders)
			}
			return null
		}
	}
}