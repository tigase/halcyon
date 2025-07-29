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

data class MessageInitiationDescription(val xmlns: String, val media: String) {
    companion object {

        fun parse(descEl: Element): MessageInitiationDescription? = descEl.xmlns?.let { xmlns ->
            descEl.attributes["media"]?.let { media ->
                MessageInitiationDescription(xmlns, media)
            }
        }
    }

    fun toElement(): Element = element("description") {
        xmlns = this@MessageInitiationDescription.xmlns
        attribute("media", this@MessageInitiationDescription.media)
    }
}
