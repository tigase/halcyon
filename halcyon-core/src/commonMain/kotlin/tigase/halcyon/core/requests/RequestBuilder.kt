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

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.ElementImpl
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.stanzas.*

typealias ResultHandler<V> = (Result<V>) -> Unit
typealias ResponseStanzaHandler<STT> = (Request<*, *, STT>, STT?) -> Unit

class XMPPError(val response: Stanza<*>?, val error: ErrorCondition, val description: String?) : Exception()

class RequestBuilderFactory(private val halcyon: AbstractHalcyon) {

	fun iq(stanza: Element): RequestBuilder<IQ, ErrorCondition, IQ> = RequestBuilder(halcyon, stanza) { it as IQ }
	fun iq(init: IQNode.() -> Unit): RequestBuilder<IQ, ErrorCondition, IQ> {
		val n = IQNode(IQ(ElementImpl(IQ.NAME)))
		n.init()
		n.id()
		val stanza = n.element as IQ
		return RequestBuilder<IQ, ErrorCondition, IQ>(halcyon, stanza) { it as IQ }
	}

	fun presence(stanza: Element): RequestBuilder<Unit, ErrorCondition, Presence> =
		RequestBuilder(halcyon, stanza) { Unit }

	fun presence(init: PresenceNode.() -> Unit): RequestBuilder<Unit, ErrorCondition, Presence> {
		val n = PresenceNode(Presence(ElementImpl(Presence.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Presence
		return RequestBuilder(halcyon, stanza) { Unit }
	}

	fun message(stanza: Element): RequestBuilder<Unit, ErrorCondition, Message> =
		RequestBuilder(halcyon, stanza) { Unit }

	fun message(init: MessageNode.() -> Unit): RequestBuilder<Unit, ErrorCondition, Message> {
		val n = MessageNode(Message(ElementImpl(Message.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Message
		return RequestBuilder(halcyon, stanza) { Unit }
	}

}

class Request<V, E, STT : Stanza<*>>(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	stanza: STT,
	timeoutDelay: Long,
	private val handler: ResultHandler<V>?,
	private val transform: (value: Any) -> V,
	private val parentRequest: Request<*, *, STT>? = null
) : AbstractRequest<V, STT>(jid, id, creationTimestamp, stanza, timeoutDelay) {

	internal var stanzaHandler: ResponseStanzaHandler<STT>? = null

	override fun createRequestNotCompletedException(): RequestNotCompletedException = RequestNotCompletedException(this)

	override fun createRequestErrorException(error: ErrorCondition, text: String?): RequestErrorException =
		RequestErrorException(this, error, text)

	private fun callResponseStanzaHandler() {
		try {
			stanzaHandler?.invoke(this, response)
		} catch (e: Throwable) {
		}
	}

	private fun requestStack(): List<Request<*, *, STT>> {
		val result = mutableListOf<Request<*, *, STT>>()

		var tmp: Request<*, *, STT>? = this
		while (tmp != null) {
			result.add(0, tmp)
			tmp = tmp.parentRequest
		}

		return result
	}

	private var calculatedResult: Result<V>? = null

	override fun processStack() {
		val requests = requestStack()

		var tmp: Any? = response


		requests.forEach { req ->
			val result = if (isTimeout) {
				Result.failure(XMPPError(response, ErrorCondition.RemoteServerTimeout, null))
			} else {
				when (response!!.attributes["type"]) {
					"result" -> {
						tmp = req.transform.invoke(tmp!!)
						Result.success(tmp)
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

			req.calculatedResult = result as (Result<Nothing>)
			if (isTimeout) req.markTimeout(false)
			else req.setResponseStanza(response!!, false)
		}
	}

	override fun callHandlers() {
		callResponseStanzaHandler()
		val tmp = calculatedResult
		if (tmp == null) throw RuntimeException()
		handler?.invoke(tmp)
	}
}

class RequestBuilder<V, ERR, STT : Stanza<*>>(
	private val halcyon: AbstractHalcyon, private val element: Element, private val transform: (value: Any) -> V
) {

	private var parentBuilder: RequestBuilder<*, *, STT>? = null

	private var timeoutDelay: Long = 30000

	private var resultHandler: ResultHandler<V>? = null

	private var responseStanzaHandler: ResponseStanzaHandler<STT>? = null

	fun build(): Request<V, ERR, STT> {
		val stanza = wrap<STT>(halcyon.modules.processSendInterceptors(element))
		return Request<V, ERR, STT>(
			stanza.to,
			stanza.id!!,
			currentTimestamp(),
			stanza,
			timeoutDelay,
			resultHandler,
			transform,
			parentBuilder?.build()
		).apply {
			this.stanzaHandler = responseStanzaHandler
		}
	}

//	fun <R : Any> map(transform: (value: STT) -> R): RequestBuilder<V,R, ERR, STT> {
//		if (parentBuilder!=null) throw IllegalStateException("Stacked maps are not allowed.")
//		val res = RequestBuilder<V,R, ERR, STT>(halcyon, element, transform)
//		res.timeoutDelay = timeoutDelay
//		res.resultHandler = null
//		res.parentBuilder = this
//		return res
//	}

	fun <R : Any> map(transform: (value: V) -> R): RequestBuilder<R, ERR, STT> {
		val xx: ((Any) -> R) = transform as (((Any) -> R))
		val res = RequestBuilder<R, ERR, STT>(halcyon, element, xx)
		res.timeoutDelay = timeoutDelay
		res.resultHandler = null
		res.parentBuilder = this
		return res
	}

	fun handleResponseStanza(handler: ResponseStanzaHandler<STT>): RequestBuilder<V, ERR, STT> {
		this.responseStanzaHandler = handler
		return this
	}

	fun response(handler: ResultHandler<V>): RequestBuilder<V, ERR, STT> {
		this.resultHandler = handler
		return this
	}

	fun timeToLive(time: Long): RequestBuilder<V, ERR, STT> {
		timeoutDelay = time
		return this
	}

	fun send(): Request<V, ERR, STT> {
		val req = build()
		halcyon.write(req)
		return req
	}

}