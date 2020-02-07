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
import tigase.halcyon.core.xmpp.stanzas.*

typealias ResultConverter<T> = (Element) -> T

abstract class Request<V, STT : Stanza<*>>(
	val jid: JID?, val id: String, val creationTimestamp: Long, val stanza: STT, var timeoutDelay: Long
) {

	protected val data = HashMap<String, Any>()

	/**
	 * `true` when no response for IQ or when stanza is not delivered to server (StreamManagement must be enabled)
	 */
	var isTimeout: Boolean = false
		protected set

	var isCompleted: Boolean = false
		private set

	var isSent: Boolean = false
		private set

	var response: STT? = null
		private set

	internal open fun markAsSent() {
		this.isSent = true
	}

	internal fun setResponseStanza(response: Element) {
		this.response = wrap(response)
		isCompleted = true
		callHandlers()
	}

	internal fun markTimeout() {
		isCompleted = true
		isTimeout = true
		callHandlers()
	}

	protected abstract fun createRequestNotCompletedException(): RequestNotCompletedException

	protected abstract fun createRequestErrorException(
		error: ErrorCondition, text: String? = null
	): RequestErrorException

	protected abstract fun callHandlers()

	data class Error(val condition: ErrorCondition, val message: String?)

	protected fun findCondition(stanza: Element): Error {
		val error = stanza.children.firstOrNull { element -> element.name == "error" } ?: return Error(
			ErrorCondition.Unknown, null
		)
		// Condition Element
		val cnd =
			error.children.firstOrNull { element -> element.name != "text" && element.xmlns == XMPPException.XMLNS }
				?: return Error(ErrorCondition.Unknown, null)
		val txt =
			error.children.firstOrNull { element -> element.name == "text" && element.xmlns == XMPPException.XMLNS }

		val c = ErrorCondition.getByElementName(cnd.name)
		return Error(c, txt?.value)
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
		return "Request[to=$jid, id=$id: ${stanza.getAsString()}]"
	}

}

abstract class AbstractIQRequest<V>(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	requestStanza: IQ,
	protected val handler: IQResponseResultHandler<V>?,
	protected var resultConverter: ResultConverter<V>?,
	timeoutDelay: Long
) : Request<V, IQ>(jid, id, creationTimestamp, requestStanza, timeoutDelay) {

	protected var value: V? = null

	fun getResult(): V? {
		if (isTimeout) throw createRequestErrorException(ErrorCondition.RemoteServerTimeout, null)
		if (!isCompleted) throw createRequestNotCompletedException()
		if (response == null) return null
		response?.let {
			if (it.attributes["type"] == "error") {
				val e = findCondition(it)
				throw createRequestErrorException(e.condition, e.message)
			}
		}
		return value
	}

	override fun callHandlers() {
		if (isTimeout) {
			handler?.invoke(
				IQResult.Error(
					this as IQRequest<V>, null, ErrorCondition.RemoteServerTimeout, null
				)
			)
		} else response?.let { received ->
			value = resultConverter?.invoke(received)
			handler?.let {
				val type = received.attributes["type"]
				if (type == "result") {
					it.invoke(IQResult.Success(this as IQRequest<V>, received, value))
				} else if (type == "error") {
					val e = findCondition(received)
					it.invoke(IQResult.Error(this as IQRequest<V>, received, e.condition, e.message))
				}
			}
		}
	}

	override fun markAsSent() {}

	override fun createRequestNotCompletedException(): RequestNotCompletedException = RequestNotCompletedException(this)

	override fun createRequestErrorException(error: ErrorCondition, text: String?): RequestErrorException =
		RequestErrorException(this, error, text)

}

expect class IQRequest<V>(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	requestStanza: IQ,
	handler: IQResponseResultHandler<V>?,
	resultConverter: ResultConverter<V>?,
	timeoutDelay: Long
) : AbstractIQRequest<V>

class PresenceRequest(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	requestStanza: Presence,
	private val stanzaHandler: StanzaStatusHandler<PresenceRequest>?,
	timeoutDelay: Long
) : Request<Unit, Presence>(jid, id, creationTimestamp, requestStanza, timeoutDelay) {

	override fun createRequestNotCompletedException(): RequestNotCompletedException = RequestNotCompletedException(this)

	override fun createRequestErrorException(
		error: ErrorCondition, text: String?
	): RequestErrorException = RequestErrorException(this, error, text)

	override fun markAsSent() {
		super.markAsSent()
		stanzaHandler?.invoke(StanzaResult.Sent(this))
	}

	override fun callHandlers() {
		if (isTimeout) stanzaHandler?.invoke(StanzaResult.NotSent(this))
	}
}

class MessageRequest(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	requestStanza: Message,
	private val stanzaHandler: StanzaStatusHandler<MessageRequest>?,
	timeoutDelay: Long
) : Request<Unit, Message>(jid, id, creationTimestamp, requestStanza, timeoutDelay) {

	override fun createRequestNotCompletedException(): RequestNotCompletedException = RequestNotCompletedException(this)

	override fun createRequestErrorException(error: ErrorCondition, text: String?): RequestErrorException =
		RequestErrorException(this, error, text)

	override fun markAsSent() {
		super.markAsSent()
		stanzaHandler?.invoke(StanzaResult.Sent(this))
	}

	override fun callHandlers() {
		if (isTimeout) stanzaHandler?.invoke(StanzaResult.NotSent(this))
	}
}
