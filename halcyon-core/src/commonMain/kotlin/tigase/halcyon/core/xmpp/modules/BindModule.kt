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

import tigase.halcyon.core.Context
import tigase.halcyon.core.Scope
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.request2.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq

class BindModule(override val context: Context) : XmppModule {

	companion object {

		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-bind"
		const val TYPE = XMLNS
	}

	override val type = TYPE
	override val criteria: Criteria? = null
	override val features = arrayOf(XMLNS)

	var boundJID: JID? by propertySimple(Scope.Session, null)
		private set

	override fun initialize() {}

	fun bind(resource: String? = null): RequestBuilder<BindResult, ErrorCondition, IQ> {
		val stanza = iq {
			type = IQType.Set
			"bind"{
				xmlns = XMLNS
				resource?.let {
					"resource"{
						value = it
					}
				}
			}
		}
		return context.request.iq(stanza).map(this::createBindResult)
	}

	private fun createBindResult(element: IQ): BindResult {
		val bind = element.getChildrenNS("bind", XMLNS)!!
		val jidElement = bind.getFirstChild("jid")!!
		val jid = JID.parse(jidElement.value!!)
		boundJID = jid
		return BindResult(jid)
	}

	override fun process(element: Element) {
		throw XMPPException(ErrorCondition.BadRequest)
	}

	data class BindResult(val jid: JID)

}