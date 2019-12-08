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

interface IQResponseHandler<T : Any> {
	fun success(request: IQRequest<T>, response: Element, value: T?)
	fun error(request: IQRequest<T>, response: Element?, error: ErrorCondition, errorMessage: String?)
}

typealias IQResponseResultHandler<T> = (IQRequest<T>, Element?, Result<T>) -> Unit

class IQHandlerHelper<T : Any> {

	private var successHandler: ((IQRequest<T>, Element, value: T?) -> Unit)? = null
	private var errorHandler: ((IQRequest<T>, Element?, ErrorCondition, String?) -> Unit)? = null

	fun success(handler: (IQRequest<T>, Element, result: T?) -> Unit) {
		this.successHandler = handler
	}

	fun error(handler: (IQRequest<T>, Element?, ErrorCondition, String?) -> Unit) {
		this.errorHandler = handler
	}

	internal fun responseHandler(): IQResponseHandler<T> = object : IQResponseHandler<T> {
		override fun success(request: IQRequest<T>, response: Element, value: T?) {
			successHandler?.invoke(request, response, value)
		}

		override fun error(
			request: IQRequest<T>, response: Element?, error: ErrorCondition, errorMessage: String?
		) {
			errorHandler?.invoke(request, response, error, errorMessage)
		}

	}
}

typealias MessageErrorHandler = (MessageRequest, Element?, ErrorCondition, String?) -> Unit

typealias PresenceErrorHandler = (PresenceRequest, Element?, ErrorCondition, String?) -> Unit