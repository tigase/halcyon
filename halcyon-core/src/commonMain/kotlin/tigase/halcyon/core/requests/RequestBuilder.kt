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
package tigase.halcyon.core.requests

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.JID

class RequestBuilder<T : Any>(
	private val halcyon: AbstractHalcyon, private val stanzaToSend: Element
) {


	private var request: Request<T>

	init {
		val j = stanzaToSend.attributes["to"]
		val jid = if (j != null) JID.parse(j) else null
		this.request = Request<T>(jid, stanzaToSend.attributes["id"]!!, currentTimestamp(), stanzaToSend)
	}

	fun resultBuilder(resultConverter: ResultConverter<T>): RequestBuilder<T> {
		request.resultConverter = resultConverter
		return this
	}

	fun send(): Request<T> {
		halcyon.requestsManager.register(request)
		halcyon.write(request)
		return request
	}

}