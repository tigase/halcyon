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

import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.IQ

sealed class IQResult<V> {

	abstract fun get(): V?

	class Success<V>(val request: IQRequest<V>, val response: IQ, private val value: V?) : IQResult<V>() {
		override fun get(): V? = value
	}

	class Error<V>(
		val request: IQRequest<V>, val response: IQ?, val error: ErrorCondition, val text: String?
	) : IQResult<V>() {

		override fun get(): V = throw XMPPException(error)
	}

	override fun toString(): String = when (this) {
		is Success -> "Success(${get()})"
		is Error -> "Error($error)"
	}

}

sealed class StanzaResult<out STT : Request<*, *>> {
	class Sent<STT : Request<*, *>>(val request: STT) : StanzaResult<STT>()
	class NotSent<STT : Request<*, *>>(val request: STT) : StanzaResult<STT>()
}
