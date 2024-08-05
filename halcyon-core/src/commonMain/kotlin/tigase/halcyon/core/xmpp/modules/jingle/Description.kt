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

class Description(
	val media: String,
	val ssrc: String?,
	val payloads: List<Payload>,
	val bandwidth: String?,
	val encryption: List<Encryption>,
	val rtcpMux: Boolean,
	val ssrcs: List<SSRC>,
	val ssrcGroups: List<SSRCGroup>,
	val hdrExts: List<HdrExt>,
) {

	enum class Senders {

		Initiator,
		Responder,
		Both
	}

	fun toElement(): Element {
		return element("description") {
			xmlns = XMLNS
			attribute("media", media)
			ssrc?.let { attribute("ssrc", it) }
			payloads.forEach {
				addChild(it.toElement())
			}
			if (encryption.isNotEmpty()) {
				addChild(element("encryption") {
					val encryptionEl = this
					encryption.forEach {
						encryptionEl.addChild(it.toElement())
					}
				})
			}
			ssrcGroups.forEach { addChild(it.toElement()) }
			ssrcs.forEach { addChild(it.toElement()) }
			bandwidth?.let {
				element("bandwidth") {
					attribute("type", it)
				}
			}
			if (rtcpMux) {
				element("rtcp-mux");
			}
			hdrExts.forEach { addChild(it.toElement()) }
		}
	}

	companion object {

		const val XMLNS = "urn:xmpp:jingle:apps:rtp:1"
		val supportedFeatures = arrayOf(XMLNS, "urn:xmpp:jingle:apps:rtp:audio", "urn:xmpp:jingle:apps:rtp:video")

		@JvmStatic
		fun parse(el: Element): Description? {
			if ("description".equals(el.name) && XMLNS.equals(el.xmlns)) {
				val media = el.attributes["media"] ?: return null
				val payloads = el.children.map { Payload.parse(it) }
					.filterNotNull()
				val ssrc = el.attributes["ssrc"]
				val bandwidth = el.getFirstChild("bandwidth")
					?.let { it.attributes["type"] }
				val rtcpMux = el.getFirstChild("rtcp-mux") != null
				val encryption = el.getFirstChild("encryption")?.children?.map { Encryption.parse(it) }
					?.filterNotNull() ?: emptyList()
				val ssrcs = el.children.map { SSRC.parse(it) }
					.filterNotNull()
				val ssrcGroups = el.children.map { SSRCGroup.parse(it) }
					.filterNotNull()
				val hdrExts = el.children.map { HdrExt.parse(it) }
					.filterNotNull()
				return Description(media, ssrc, payloads, bandwidth, encryption, rtcpMux, ssrcs, ssrcGroups, hdrExts)
			}
			return null
		}
	}

}