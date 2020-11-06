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
package tigase.halcyon.core.xml.parser

interface SimpleHandler {

	fun error(errorMessage: String)

	fun startElement(name: String, attr_names: Array<String?>?, attr_values: Array<String?>?)

	fun elementCData(cdata: String)

	fun endElement(name: String): Boolean

	fun otherXML(other: String)

	fun saveParserState(state: Any?)

	fun restoreParserState(): Any?

}