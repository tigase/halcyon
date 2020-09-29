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
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.modules.AbstractXmppIQModule
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.response
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq

class PingModule(context: Context) : AbstractXmppIQModule(
	context, TYPE, arrayOf(XMLNS), Criterion.chain(Criterion.name(IQ.NAME), Criterion.xmlns(XMLNS))
) {

	companion object {

		const val XMLNS = "urn:xmpp:ping"
		const val TYPE = XMLNS
	}

	fun ping(jid: JID? = null): RequestBuilder<Pong, ErrorCondition, IQ> {
		val stanza = iq {
			type = IQType.Get
			if (jid != null) to = jid
			"ping"{
				xmlns = XMLNS
			}
		}
		val time0 = currentTimestamp()
		return context.request.iq(stanza).map { Pong(currentTimestamp() - time0) }
	}

	override fun processGet(element: IQ) {
		context.writer.writeDirectly(response(element) { })
	}

	override fun processSet(element: IQ) {
		throw XMPPException(ErrorCondition.NotAcceptable)
	}

	data class Pong(val time: Long)

}