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
package tigase.halcyon.core.xml.parser

import tigase.halcyon.core.xml.Element

abstract class StreamParser {

	private val parser = SimpleParser()

	private val handler = XMPPDomHandler(
		onStreamClosed = ::onStreamClosed,
		onNextElement = ::onNextElement,
		onStreamStarted = ::onStreamStarted,
		onParseError = ::onParseError
	)

	fun parse(data: String) {
		val ca = CharArray(data.length)
		for (i in 0 until data.length) {
			ca[i] = data[i]
		}
		parser.parse(handler, ca)
	}

	fun parse(data: CharArray) {
		parser.parse(handler, data)
	}

	fun parse(data: CharArray, offset: Int, len: Int) {
		parser.parse(handler, data, offset, len)
	}

	abstract fun onNextElement(element: Element)
	abstract fun onStreamClosed()
	abstract fun onStreamStarted(attrs: Map<String, String>)
	abstract fun onParseError(errorMessage: String)

}