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

interface ResponseHandler<T : Any> {
	fun success(request: Request<T>, response: Element, value: T?)
	fun error(request: Request<T>, response: Element?, error: ErrorCondition)
}

typealias ResponseResultHandler<T> = (Request<T>, Element?, Result<T>) -> Unit

class HandlerHelper<T : Any> {

	private var successHandler: ((Request<T>, Element, value: T?) -> Unit)? = null
	private var errorHandler: ((Request<T>, Element?, ErrorCondition) -> Unit)? = null

	fun success(handler: (Request<T>, Element, result: T?) -> Unit) {
		this.successHandler = handler
	}

	fun error(handler: (Request<T>, Element?, ErrorCondition) -> Unit) {
		this.errorHandler = handler
	}

	internal fun responseHandler(): ResponseHandler<T> = object : ResponseHandler<T> {
		override fun success(request: Request<T>, response: Element, value: T?) {
			successHandler?.invoke(request, response, value)
		}

		override fun error(request: Request<T>, response: Element?, error: ErrorCondition) {
			errorHandler?.invoke(request, response, error)
		}

	}

}