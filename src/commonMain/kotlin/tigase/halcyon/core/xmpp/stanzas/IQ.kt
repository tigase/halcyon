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
package tigase.halcyon.core.xmpp.stanzas

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.setAtt
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

enum class IQType(val value: String) { Error("error"),
	Get("get"),
	Result("result"),
	Set("set")
}

class IQ(wrappedElement: Element) : Stanza<IQType>(wrappedElement) { companion object {

	const val NAME = "iq"
}

	override var type: IQType
		set(value) = setAtt("type", value.value)
		get() {
			val tp = attributes["type"]
			return tp?.let {
				IQType.values().firstOrNull { te -> te.value == it }
			} ?: throw XMPPException(ErrorCondition.BadRequest, "Unknown stanza type '$tp'")
		}

}
