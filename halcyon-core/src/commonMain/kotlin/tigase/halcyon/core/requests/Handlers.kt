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
import tigase.halcyon.core.xmpp.stanzas.IQ

typealias IQResponseResultHandler<T> = (IQResult<T>) -> Unit

interface IQResponseHandler<T : Any> {
	fun success(request: IQRequest<T>, response: IQ, value: T?)
	fun error(request: IQRequest<T>, response: IQ?, error: ErrorCondition, errorMessage: String?)
}

class IQHandlerHelper<T : Any> {

	private var successHandler: ((IQRequest<T>, IQ, value: T?) -> Unit)? = null
	private var errorHandler: ((IQRequest<T>, IQ?, ErrorCondition, String?) -> Unit)? = null

	fun success(handler: (IQRequest<T>, IQ, result: T?) -> Unit) {
		this.successHandler = handler
	}

	fun error(handler: (IQRequest<T>, IQ?, ErrorCondition, String?) -> Unit) {
		this.errorHandler = handler
	}

	internal fun responseHandler(): IQResponseHandler<T> = object : IQResponseHandler<T> {
		override fun success(request: IQRequest<T>, response: IQ, value: T?) {
			successHandler?.invoke(request, response, value)
		}

		override fun error(
			request: IQRequest<T>, response: IQ?, error: ErrorCondition, errorMessage: String?
		) {
			errorHandler?.invoke(request, response, error, errorMessage)
		}

	}
}

typealias StanzaStatusHandler<T> = (StanzaResult<T>) -> Unit