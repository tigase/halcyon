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

import kotlin.jvm.JvmStatic
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element

class Transport(
    val ufrag: String?,
    val pwd: String?,
    val candidates: List<Candidate>,
    val fingerprint: Fingerprint?
) {

    fun toElement(): Element = element("transport") {
        xmlns = XMLNS
        fingerprint?.let {
            this.addChild(it.toElement())
        }
        candidates.forEach {
            this.addChild(it.toElement())
        }
        ufrag?.let { attribute("ufrag", it) }
        pwd?.let { attribute("pwd", it) }
    }

    companion object {

        const val XMLNS = "urn:xmpp:jingle:transports:ice-udp:1"

        val supportedFeatures = arrayOf(XMLNS, "urn:xmpp:jingle:apps:dtls:0")

        @JvmStatic
        fun parse(el: Element): Transport? {
            if (!("transport".equals(el.name) && XMLNS.equals(el.xmlns))) {
                return null
            }
            val candidates: List<Candidate> = el.children.map { Candidate.parse(it) }
                .filterNotNull()
            val fingerprint = el.children.map { Fingerprint.parse(it) }
                .firstOrNull()
            return Transport(el.attributes["ufrag"], el.attributes["pwd"], candidates, fingerprint)
        }
    }
}
