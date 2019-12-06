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
import tigase.halcyon.core.xmpp.XMPPException

typealias ResultConverter<T> = (Element) -> T

abstract class AbstractRequest<V : Any, RQTYP : Request<*>>(
	val jid: JID?, val id: String, val creationTimestamp: Long, val requestStanza: Element
) {

	protected val data = HashMap<String, Any>()

	protected var handler: ResponseHandler<V>? = null

	internal var resultConverter: ResultConverter<V>? = null

	protected var isTimeout: Boolean = false

	var isCompleted: Boolean = false
		internal set

	var responseStanza: Element? = null
		private set

	private var value: V? = null

	/**
	 * Delay to timeout this request in milliseconds.
	 */
	var timeoutDelay: Long = 30000

	internal fun setResponseStanza(response: Element) {
		responseStanza = response
		value = if (resultConverter != null) resultConverter!!.invoke(responseStanza!!) else null
		isCompleted = true
		callHandlers()
	}

	@Suppress("UNCHECKED_CAST")
	protected fun createRequestNotCompletedException(): RequestNotCompletedException =
		RequestNotCompletedException(this as RQTYP)

	@Suppress("UNCHECKED_CAST")
	protected fun createRequestErrorException(error: ErrorCondition): RequestErrorException =
		RequestErrorException(this as RQTYP, error)

	fun getResult(): V? {
		if (isTimeout) throw createRequestErrorException(ErrorCondition.RemoteServerTimeout)
		if (!isCompleted) throw createRequestNotCompletedException()
		if (responseStanza == null) return null
		responseStanza?.let {
			if (it.attributes["type"] == "error") {
				throw createRequestErrorException(findCondition(it))
			}
		}
		return value
	}

	internal fun setTimeout() {
		isCompleted = true

		val stanzaType = requestStanza.attributes["type"]
		if (stanzaType == "get" || stanzaType == "set") {
			isTimeout = true
		}
		callHandlers()
	}

//	protected abstract fun callHandlers()

	open fun callHandlers() {
		if (isTimeout) handler?.error(this as Request<V>, null, ErrorCondition.RemoteServerTimeout)

		if (responseStanza == null) return

		handler?.let {
			val type = responseStanza!!.attributes["type"]
			if (type == "result") {
				it.success(this as Request<V>, responseStanza!!, getResult())
			} else if (type == "error") {
				it.error(this as Request<V>, responseStanza!!, findCondition(responseStanza!!))
			}
		}
	}

	protected fun findCondition(stanza: Element): ErrorCondition {
		val error = stanza.children.firstOrNull { element -> element.name == "error" }
			?: return ErrorCondition.Unknown
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

		override fun error(request: Request<V>, response: Element?, error: ErrorCondition) {
			handler.invoke(request, response, Result.Error(response, error))
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

}