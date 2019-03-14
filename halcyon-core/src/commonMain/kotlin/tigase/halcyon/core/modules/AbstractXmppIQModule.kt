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
package tigase.halcyon.core.modules

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

abstract class AbstractXmppIQModule(
	type: String, features: Array<String>, criteria: tigase.halcyon.core.modules.Criteria
) : tigase.halcyon.core.modules.AbstractXmppModule(type, features, criteria) {

	final override fun process(element: Element) {
		val type = element.attributes["type"]
		when (type) {
			"set" -> processSet(element)
			"get" -> processGet(element)
			else -> throw XMPPException(ErrorCondition.BadRequest)
		}
	}

	abstract fun processGet(element: Element)

	abstract fun processSet(element: Element)

}