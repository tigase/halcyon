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

import getTypeAttr
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.StanzaType
import tigase.halcyon.core.xmpp.XMPPException

abstract class AbstractRequest<V : Any>(
	val jid: JID?, val id: String, val creationTimestamp: Long, val requestStanza: Element
) {

	protected val data = HashMap<String, Any>()

	protected var handler: ResponseHandler<V>? = null

	internal var resultConverter: ResultConverter<V>? = null

	protected var timeout: Boolean = false

	var completed: Boolean = false
		internal set

	var responseStanza: Element? = null
		internal set(value) {
			field = value
			completed = true
			callHandlers()
		}

	/**
	 * Delay to timeout this request in milliseconds.
	 */
	var timeoutDelay: Long = 30000

	protected abstract fun createRequestTimeoutException(): RequestTimeoutException

	protected abstract fun createRequestNotCompletedException(): RequestNotCompletedException

	protected abstract fun createRequestErrorException(error: ErrorCondition): RequestErrorException

	fun getResult(): V? {
		if (timeout) throw createRequestTimeoutException()
		if (!completed) throw createRequestNotCompletedException()
		if (responseStanza == null) return null
		responseStanza?.let {
			if (it.getTypeAttr() == StanzaType.Error) {
				throw createRequestErrorException(findCondition(it))
			}
		}
		return if (resultConverter != null) resultConverter!!.invoke(responseStanza!!) else null
	}

	internal fun setTimeout() {
		completed = true
		callTimeout()
	}

	protected abstract fun callHandlers()

	protected abstract fun callTimeout()

	protected fun findCondition(stanza: Element): ErrorCondition {
		val error = stanza.children.firstOrNull { element -> element.name == "error" } ?: return ErrorCondition.Unknown
		val cnd = error.children.firstOrNull { element -> element.xmlns == XMPPException.XMLNS }
			?: return ErrorCondition.Unknown
		return ErrorCondition.getByElementName(cnd.name)
	}

	fun response(handler: ResponseResultHandler<V>) {
		this.handler = ResponseResponseResult(handler)
		callHandlers()
	}

	fun response(handler: ResponseHandler<V>) {
		this.handler = handler
		callHandlers()
	}

	fun handle(init: HandlerHelper<V>.() -> Unit) {
		val callback = HandlerHelper<V>()
		callback.init()
		this.handler = callback.responseHandler()
		callHandlers()
	}

	inner class ResponseResponseResult(private val handler: ResponseResultHandler<V>) : ResponseHandler<V> {
		override fun success(request: Request<V>, response: Element, value: V?) {
			handler.invoke(request, response, Result.Success(response, value))
		}

		override fun error(request: Request<V>, response: Element, error: ErrorCondition) {
			handler.invoke(request, response, Result.Error(response, error))
		}

		override fun timeout(request: Request<V>) {
			handler.invoke(request, null, Result.Timeout())
		}
	}

	fun isSet(param: String): Boolean {
		val v = data[param]
		return v != null && v is Boolean && v
	}

	fun setData(name: String, value: Any) {
		data[name] = value
	}

	fun getData(name: String): Any? {
		return data[name]
	}

	override fun toString(): String {
		return "Request[to=$jid, id=$id: ${requestStanza.getAsString()}]"
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is AbstractRequest<*>) return false

		if (jid != other.jid) return false
		if (id != other.id) return false
		if (creationTimestamp != other.creationTimestamp) return false

		return true
	}

	override fun hashCode(): Int {
		var result = jid?.hashCode() ?: 0
		result = 31 * result + id.hashCode()
		result = 31 * result + creationTimestamp.hashCode()
		return result
	}
}

typealias ResultConverter<T> = (Element) -> T
