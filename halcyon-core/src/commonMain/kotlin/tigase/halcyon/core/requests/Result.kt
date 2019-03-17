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

import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

sealed class Result<out V : Any> {

	abstract fun get(): V?

	class Success<out V : Any>(val responseStanza: Element, val value: V?) : Result<V>() {
		override fun get(): V? = value
	}

	class Error<out V : Any>(val responseStanza: Element, val error: ErrorCondition) : Result<V>() {
		override fun get(): V = throw XMPPException(error)
	}

	class Timeout<out V : Any> : Result<V>() {
		override fun get(): V = throw HalcyonException("Request timeout")
	}

}