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
package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element

fun parseRSM(element: Element): RSM {
	if (element.name != RSM.NAME) throw HalcyonException("Invalid RSM element name")
	if (element.xmlns != RSM.XMLNS) throw HalcyonException("Invalid RSM element XMLNS")
	var index: Int? = null
	var first: String? = null
	var last: String? = null
	var count: Int? = null
	var max: Int? = null

	element.getFirstChild("first")?.let {
		first = it.value
		index = it.attributes["index"]?.toInt()
	}
	element.getFirstChild("last")?.let { last = it.value }
	element.getFirstChild("count")?.let { count = it.value?.toInt() }
	element.getFirstChild("max")?.let { max = it.value?.toInt() }

	return RSM(index, first, last, null, null, count, max)
}

data class RSM(
	val index: Int? = null,
	val first: String? = null,
	val last: String? = null,
	val after: String? = null,
	val before: String? = null,
	val count: Int? = null,
	val max: Int? = null
//	var retrieveLastPage: Boolean = false
) {

	companion object {

		const val XMLNS = "http://jabber.org/protocol/rsm"
		const val NAME = "set"
		fun fromElement(element: Element): RSM = parseRSM(element)
	}

	fun toElement(): Element {
		return element(NAME) {
			xmlns = XMLNS

			after?.let {
				"after"{
					+it
				}
			}
			before?.let {
				"before"{
					+it
				}
			}
			index?.let {
				"index"{
					+"$it"
				}
			}
			max?.let {
				"max"{
					+"$it"
				}
			}

		}
	}
}
