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

import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.stanzas.IQ

actual class IQRequest<V> actual constructor(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	requestStanza: IQ,
	handler: IQResponseResultHandler<V>?,
	resultConverter: ResultConverter<V>?,
	timeoutDelay: Long
) : AbstractIQRequest<V>(jid, id, creationTimestamp, requestStanza, handler, resultConverter, timeoutDelay) {

	private val lock = Object()

	fun getResultWait(): V? {
		if (isCompleted) return getResult()
		synchronized(lock) {
			lock.wait()
		}
		return getResult()
	}

	override fun callHandlers() {
		super.callHandlers()
		synchronized(lock) {
			lock.notify()
		}
	}

}