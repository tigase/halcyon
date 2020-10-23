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
import tigase.halcyon.core.xmpp.stanzas.Stanza
import tigase.halcyon.core.xmpp.stanzas.wrap

abstract class AbstractRequest<V, STT : Stanza<*>>(
	val jid: JID?,
	val id: String,
	val creationTimestamp: Long,
	val stanza: STT,
	var timeoutDelay: Long,
	private val handler: ResultHandler<V>?,
	private val transform: (value: Any) -> V,
	private val parentRequest: AbstractRequest<*, STT>? = null,
	private val callHandlerOnSent: Boolean
) {

	data class Error(val condition: ErrorCondition, val message: String?)

	private val data = HashMap<String, Any>()

	/**
	 * `true` when no response for IQ or when stanza is not delivered to server (StreamManagement must be enabled)
	 */
	var isTimeout: Boolean = false
		private set

	var isCompleted: Boolean = false
		private set

	var isSent: Boolean = false
		private set

	var response: STT? = null
		private set

	private var calculatedResult: Result<V>? = null

	internal var stanzaHandler: ResponseStanzaHandler<STT>? = null

	private fun requestStack(): List<AbstractRequest<*, STT>> {
		val result = mutableListOf<AbstractRequest<*, STT>>()

		var tmp: AbstractRequest<*, STT>? = this
		while (tmp != null) {
			result.add(0, tmp)
			tmp = tmp.parentRequest
		}

		return result
	}

	fun processStack() {
		val requests = requestStack()

		// Currently returned value. Will be updated by map()'s from stack.
		var tmpValue: Any? = response
		// Result calculated in previous step
		var tmpResult: Result<Any?>? = null

		requests.forEach { req ->
			tmpResult = if (tmpResult != null && (tmpResult as Result<Any?>).isFailure) tmpResult else if (isTimeout) {
				Result.failure(XMPPError(response, ErrorCondition.RemoteServerTimeout, null))
			} else {
				when (response!!.attributes["type"]) {
					"result" -> {
						try {
							tmpValue = req.transform.invoke(tmpValue!!)
							Result.success(tmpValue)
						} catch (e: XMPPError) {
							Result.failure<XMPPError>(e)
						}
					}
					"error" -> {
						val e = findCondition(response!!)
						Result.failure(XMPPError(response!!, e.condition, e.message))
					}
					else -> {
						Result.failure(XMPPError(response!!, ErrorCondition.UnexpectedRequest, null))
					}
				}
			}

			req.calculatedResult = tmpResult as (Result<Nothing>)
			if (isTimeout) req.markTimeout(false)
			else req.setResponseStanza(response!!, false)
		}
	}

	internal fun setResponseStanza(response: Element, processStack: Boolean = true) {
		this.response = wrap(response)
		isCompleted = true
		if (processStack) processStack() else callHandlers()
	}

	internal open fun markAsSent(processStack: Boolean = true) {
		var tmp: Any? = Unit
		requestStack().forEach { req ->
			req.isSent = true

			if (callHandlerOnSent) {
				tmp = req.transform.invoke(tmp!!)
				val res = Result.success(tmp!!)
				req.calculatedResult = res as (Result<Nothing>)
				req.handler?.invoke(res)
			}
		}
	}

	internal fun markTimeout(processStack: Boolean = true) {
		isCompleted = true
		isTimeout = true
		if (processStack) processStack() else callHandlers()
	}

	private fun callHandlers() {
		callResponseStanzaHandler()
		val tmp = calculatedResult
		if (tmp == null) throw RuntimeException("No calculated result")
		handler?.invoke(tmp)
	}

	private fun findCondition(stanza: Element): Error {
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

	private fun callResponseStanzaHandler() {
		try {
			stanzaHandler?.invoke(this, response)
		} catch (e: Throwable) {
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
		return "Request[${stanza.getAsString(deep = 2, showValue = false)}]"
	}

}
