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
package tigase.halcyon.core.xml

class EscapeUtils private constructor() {

    companion object {

        private val ENTITIES = arrayOf(
            arrayOf("&", "&amp;"),
            arrayOf("<", "&lt;"),
            arrayOf(">", "&gt;"),
            arrayOf("\"", "&quot;"),
            arrayOf("'", "&apos;")
        )

        fun escape(str: String?): String? {
            var v: String? = str ?: return null
            if (v!!.isEmpty()) {
                return v
            }
            for (i in ENTITIES.indices) {
                v = v!!.replace(ENTITIES[i][0], ENTITIES[i][1])
            }
            return v
        }

        fun unescape(str: String?): String? {
            var v: String? = str ?: return null
            if (v!!.isEmpty()) {
                return v
            }
            for (i in ENTITIES.indices.reversed()) {
                v = v!!.replace(ENTITIES[i][1], ENTITIES[i][0])
            }
            return v
        }
    }
}
