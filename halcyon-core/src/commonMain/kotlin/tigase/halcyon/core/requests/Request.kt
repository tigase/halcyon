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
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.Presence
import tigase.halcyon.core.xmpp.stanzas.Stanza

typealias ResultConverter<T> = (Element) -> T

abstract class Request<V : Any, STT : Stanza<*>>(
	val jid: JID?, val id: String, val creationTimestamp: Long, val requestStanza: STT, var timeoutDelay: Long
) {

	protected val data = HashMap<String, Any>()

	/**
	 * `true` when no response for IQ or when stanza is not delivered to server (StreamManagement must be enabled)
	 */
	protected var isTimeout: Boolean = false

	var isCompleted: Boolean = false
		internal set

	var isSent: Boolean = false
		internal set(value) {
			field = value
			if (value && requestStanza.name != IQ.NAME) {
				isCompleted = true
			}
		}

	var responseStanza: Element? = null
		private set

	internal fun setResponseStanza(response: Element) {
		responseStanza = response
		isCompleted = true
		callHandlers()
	}

	protected abstract fun createRequestNotCompletedException(): RequestNotCompletedException

	protected abstract fun createRequestErrorException(
		error: ErrorCondition, text: String? = null
	): RequestErrorException

	internal fun setTimeout() {
		isCompleted = true

		val stanzaType = requestStanza.attributes["type"]
		if (stanzaType == "get" || stanzaType == "set") {
			isTimeout = true
		}
		callHandlers()
	}

//	protected abstract fun callHandlers()

	protected abstract fun callHandlers()

	data class Error(val condition: ErrorCondition, val message: String?)

	protected fun findCondition(stanza: Element): Error {
		val error = stanza.children.firstOrNull { element -> element.name == "error" } ?: return Error(
			ErrorCondition.Unknown, null
		)
		val cnd =
			error.children.firstOrNull { element -> element.xmlns == XMPPException.XMLNS } ?: return Error(
				ErrorCondition.Unknown, null
			)

		val c = ErrorCondition.getByElementName(cnd.name)

		return Error(c, null)
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

abstract class AbstractIQRequest<V : Any>(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	requestStanza: IQ,
	private val handler: IQResponseHandler<V>?,
	private var resultConverter: ResultConverter<V>?,
	timeoutDelay: Long
) : Request<V, IQ>(jid, id, creationTimestamp, requestStanza, timeoutDelay) {

	private var value: V? = null

	fun getResult(): V? {
		if (isTimeout) throw createRequestErrorException(ErrorCondition.RemoteServerTimeout, null)
		if (!isCompleted) throw createRequestNotCompletedException()
		if (responseStanza == null) return null
		responseStanza?.let {
			if (it.attributes["type"] == "error") {
				val e = findCondition(it)
				throw createRequestErrorException(e.condition, e.message)
			}
		}
		return value
	}

	override fun callHandlers() {
		if (isTimeout) {
			handler?.error(
				this as IQRequest<V>, null, ErrorCondition.RemoteServerTimeout, null
			)
		} else if (responseStanza != null) {
			handler?.let {
				val type = responseStanza!!.attributes["type"]
				if (type == "result") {
					it.success(this as IQRequest<V>, responseStanza!!, getResult())
				} else if (type == "error") {
					val e = findCondition(responseStanza!!)
					it.error(this as IQRequest<V>, responseStanza!!, e.condition, e.message)
				}
			}
		}
	}

	override fun createRequestNotCompletedException(): RequestNotCompletedException =
		RequestNotCompletedException(this)

	override fun createRequestErrorException(
		error: ErrorCondition, text: String?
	): RequestErrorException = RequestErrorException(this, error)

}

expect class IQRequest<V : Any>(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	requestStanza: IQ,
	handler: IQResponseHandler<V>?,
	resultConverter: ResultConverter<V>?,
	timeoutDelay: Long
) : AbstractIQRequest<V>

class PresenceRequest(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	requestStanza: Presence,
	private val errorHandler: PresenceErrorHandler?,
	timeoutDelay: Long
) : Request<Unit, Presence>(jid, id, creationTimestamp, requestStanza, timeoutDelay) {

	override fun createRequestNotCompletedException(): RequestNotCompletedException =
		RequestNotCompletedException(this)

	override fun createRequestErrorException(
		error: ErrorCondition, text: String?
	): RequestErrorException = RequestErrorException(this, error)

	override fun callHandlers() {
		if (isTimeout) errorHandler?.invoke(this, null, ErrorCondition.RemoteServerTimeout, null)
		else if (responseStanza != null && responseStanza!!.attributes["type"] == "error") {
			val e = findCondition(responseStanza!!)
			errorHandler?.invoke(this, responseStanza, e.condition, e.message)
		}
	}
}

class MessageRequest(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	requestStanza: Message,
	private val errorHandler: MessageErrorHandler?,
	timeoutDelay: Long
) : Request<Unit, Message>(jid, id, creationTimestamp, requestStanza, timeoutDelay) {

	override fun createRequestNotCompletedException(): RequestNotCompletedException =
		RequestNotCompletedException(this)

	override fun createRequestErrorException(
		error: ErrorCondition, text: String?
	): RequestErrorException = RequestErrorException(this, error)

	override fun callHandlers() {
		if (isTimeout) errorHandler?.invoke(this, null, ErrorCondition.RemoteServerTimeout, null)
		else if (responseStanza != null && responseStanza!!.attributes["type"] == "error") {
			val e = findCondition(responseStanza!!)
			errorHandler?.invoke(this, responseStanza, e.condition, e.message)
		}
	}
}
