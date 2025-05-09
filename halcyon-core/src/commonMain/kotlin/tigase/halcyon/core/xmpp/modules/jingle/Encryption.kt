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

class Encryption(
    val cryptoSuite: String,
    val keyParams: String,
    val sessionParams: String?,
    val tag: String
) {

    fun toElement(): Element = element("crypto") {
        attribute("crypto-suite", cryptoSuite)
        attribute("key-params", keyParams)
        sessionParams?.let { attribute("session-params", it) }
        attribute("tag", tag)
    }

    companion object {

        @JvmStatic
        fun parse(el: Element): Encryption? {
            if ("crypto".equals(el.name)) {
                val cryptoSuite = el.attributes["crypto-suite"] ?: return null
                val keyParams = el.attributes["key-params"] ?: return null
                val sessionParams = el.attributes["session-params"]
                val tag = el.attributes["tag"] ?: return null

                return Encryption(cryptoSuite, keyParams, sessionParams, tag)
            }
            return null
        }
    }
}
