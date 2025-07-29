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

class Fingerprint(val hash: String, val value: String, val setup: Setup) {

    enum class Setup {
        actpass,
        active,
        passive
    }

    fun toElement(): Element = element("fingerprint") {
        xmlns = "urn:xmpp:jingle:apps:dtls:0"
        attribute("hash", hash)
        attribute("setup", setup.name)
        value = this@Fingerprint.value
    }

    companion object {

        @JvmStatic
        fun parse(el: Element): Fingerprint? {
            if ("fingerprint".equals(el.name) && "urn:xmpp:jingle:apps:dtls:0".equals(el.xmlns)) {
                val hash = el.attributes["hash"] ?: return null
                val setup = el.attributes["setup"]?.let { Setup.valueOf(it) } ?: return null
                val value = el.value ?: return null
                return Fingerprint(hash, value, setup)
            }
            return null
        }
    }
}
