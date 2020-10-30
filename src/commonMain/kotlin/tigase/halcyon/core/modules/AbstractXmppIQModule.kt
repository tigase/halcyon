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

import tigase.halcyon.core.Context
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.wrap

abstract class AbstractXmppIQModule(
	context: Context, type: String, features: Array<String>, criteria: Criteria
) : AbstractXmppModule(context, type, features, criteria) {

	final override fun process(element: Element) {
		val iq: IQ = wrap(element)
		when (iq.type) {
			IQType.Set -> processSet(iq)
			IQType.Get -> processGet(iq)
			else -> throw XMPPException(ErrorCondition.BadRequest)
		}
	}

	abstract fun processGet(element: IQ)

	abstract fun processSet(element: IQ)

}