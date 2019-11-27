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
import tigase.halcyon.core.SessionObject
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.StanzaType
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.iq

class BindModule : XmppModule {

	companion object {
		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-bind"
		const val TYPE = XMLNS

		fun getBindedJID(sessionObject: SessionObject): JID? = sessionObject.getProperty(XMLNS)
	}

	override val type = TYPE
	override lateinit var context: Context
	override val criteria: Criteria? = null
	override val features = arrayOf(XMLNS)

	override fun initialize() {}

	fun bind(resource: String? = null): Request<BindResult> {
		val stanza = iq {
			type = StanzaType.Set
			"bind"{
				xmlns = XMLNS
				resource?.let {
					"resource"{
						value = it
					}
				}
			}
		}
		return context.requestBuilder<BindResult>(stanza).resultBuilder { element -> createBindResult(element) }.send()
	}

	private fun createBindResult(element: Element): BindResult {
		val bind = element.getChildrenNS("bind", XMLNS)!!
		val jidElement = bind.getFirstChild("jid")!!
		val jid = JID.parse(jidElement.value!!)
		context.sessionObject.setProperty(XMLNS, jid)
		return BindResult(jid)
	}

	override fun process(element: Element) {
		throw XMPPException(ErrorCondition.BadRequest)
	}

	data class BindResult(val jid: JID)

}