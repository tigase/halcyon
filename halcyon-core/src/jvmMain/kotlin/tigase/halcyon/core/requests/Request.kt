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

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID

actual class Request<V : Any> actual constructor(
	jid: JID?, id: String, creationTimestamp: Long, requestStanza: Element
) : AbstractRequest<V>(jid, id, creationTimestamp, requestStanza) {

	override fun createRequestTimeoutException(): RequestTimeoutException = RequestTimeoutException(this)

	override fun createRequestNotCompletedException(): RequestNotCompletedException =
		RequestNotCompletedException(this)

	override fun createRequestErrorException(error: ErrorCondition): RequestErrorException =
		RequestErrorException(this, error)

	private val lock = Object()

	fun getResultWait(): V? {
		if (isCompleted) return getResult()
		synchronized(lock) {
			lock.wait()
		}
		return getResult()
	}

	override fun callHandlers() {
		if (responseStanza == null) return

		handler?.let {
			val type = responseStanza!!.attributes["type"]
			if (type == "result") {
				it.success(this, responseStanza!!, getResult())
			} else if (type == "error") {
				it.error(this, responseStanza!!, findCondition(responseStanza!!))
			}
		}
		synchronized(lock) {
			lock.notify()
		}
	}

	override fun callTimeout() {
		val stanzaType = requestStanza.attributes["type"]
		if (stanzaType == "get" || stanzaType == "set") {
			isTimeout = true
			handler?.timeout(this)
		}
		synchronized(lock) {
			lock.notify()
		}
	}

}