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

import tigase.halcyon.core.Context
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.ElementImpl
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.stanzas.*

typealias ResultHandler<V> = (Result<V>) -> Unit
typealias ResponseStanzaHandler<STT> = (Request<*, STT>, STT?) -> Unit

class XMPPError(val response: Stanza<*>?, val error: ErrorCondition, val description: String?) : Exception(description)

class RequestBuilderFactory(private val context: Context) {

	fun iq(stanza: Element): RequestBuilder<IQ, IQ> = RequestBuilder(context, stanza) { it as IQ }
	fun iq(init: IQNode.() -> Unit): RequestBuilder<IQ, IQ> {
		val n = IQNode(IQ(ElementImpl(IQ.NAME)))
		n.init()
		n.id()
		val stanza = n.element as IQ
		return RequestBuilder(context, stanza) { it as IQ }
	}

	fun presence(stanza: Element): RequestBuilder<Unit, Presence> = RequestBuilder(context, stanza, true) { Unit }

	fun presence(init: PresenceNode.() -> Unit): RequestBuilder<Unit, Presence> {
		val n = PresenceNode(Presence(ElementImpl(Presence.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Presence
		return RequestBuilder(context, stanza, true) { Unit }
	}

	fun message(stanza: Element): RequestBuilder<Unit, Message> = RequestBuilder(context, stanza, true) { Unit }

	fun message(init: MessageNode.() -> Unit): RequestBuilder<Unit, Message> {
		val n = MessageNode(Message(ElementImpl(Message.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Message
		return RequestBuilder(context, stanza, true) { Unit }
	}

}

class RequestBuilder<V, STT : Stanza<*>>(
	private val halcyon: Context,
	private val element: Element,
	private val callHandlerOnSent: Boolean = false,
	private val transform: (value: Any) -> V
) {

	private var requestName: String? = null

	private var parentBuilder: RequestBuilder<*, STT>? = null

	private var timeoutDelay: Long = 30000

	private var resultHandler: ResultHandler<V>? = null

	private var responseStanzaHandler: ResponseStanzaHandler<STT>? = null

	fun build(): Request<V, STT> {
		val stanza = wrap<STT>(halcyon.modules.processSendInterceptors(element))
		return Request(
			stanza.to,
			stanza.id!!,
			currentTimestamp(),
			stanza,
			timeoutDelay,
			resultHandler,
			transform,
			parentBuilder?.build(),
			callHandlerOnSent
		).apply {
			this.stanzaHandler = responseStanzaHandler
			this.requestName = this@RequestBuilder.requestName
		}
	}

	@Suppress("UNCHECKED_CAST")
	fun <R : Any> map(transform: (value: V) -> R): RequestBuilder<R, STT> {
		val xx: ((Any) -> R) = transform as (((Any) -> R))
		val res = RequestBuilder<R, STT>(halcyon, element, callHandlerOnSent, xx)
		res.timeoutDelay = timeoutDelay
		res.resultHandler = null
		res.parentBuilder = this
		return res
	}

	fun handleResponseStanza(name: String? = null, handler: ResponseStanzaHandler<STT>): RequestBuilder<V, STT> {
		this.responseStanzaHandler = handler
		if (requestName != null) this.requestName = requestName
		return this
	}

	fun response(requestName: String? = null, handler: ResultHandler<V>): RequestBuilder<V, STT> {
		this.resultHandler = handler
		if (requestName != null) this.requestName = requestName
		return this
	}

	fun timeToLive(time: Long): RequestBuilder<V, STT> {
		timeoutDelay = time
		return this
	}

	fun send(): Request<V, STT> {
		val req = build()
		halcyon.writer.write(req)
		return req
	}

	fun name(requestName: String): RequestBuilder<V, STT> {
		this.requestName = requestName
		return this
	}

}

class ConsumerPublisher<CSR> {

	val observers = mutableSetOf<((CSR) -> Unit)>()

	fun publish(data: CSR) {
		observers.forEach { it.invoke(data) }
	}

}

class RequestConsumerBuilder<CSR, V, STT : Stanza<*>>(
	private val halcyon: Context,
	private val element: Element,
	private val callHandlerOnSent: Boolean = false,
	private val transform: (value: Any) -> V
) {

	private var requestName: String? = null

	internal val publisher = ConsumerPublisher<CSR>()

	private var parentBuilder: RequestConsumerBuilder<CSR, *, STT>? = null

	private var timeoutDelay: Long = 30000

	private var resultHandler: ResultHandler<V>? = null

	private var responseStanzaHandler: ResponseStanzaHandler<STT>? = null

	fun build(): Request<V, STT> {
		val stanza = wrap<STT>(halcyon.modules.processSendInterceptors(element))
		return Request(
			stanza.to,
			stanza.id!!,
			currentTimestamp(),
			stanza,
			timeoutDelay,
			resultHandler,
			transform,
			parentBuilder?.build(),
			callHandlerOnSent
		).apply {
			this.stanzaHandler = responseStanzaHandler
			this.requestName = this@RequestConsumerBuilder.requestName
		}
	}

	@Suppress("UNCHECKED_CAST")
	fun <R : Any> map(transform: (value: V) -> R): RequestConsumerBuilder<CSR, R, STT> {
		val xx: ((Any) -> R) = transform as (((Any) -> R))
		val res = RequestConsumerBuilder<CSR, R, STT>(halcyon, element, callHandlerOnSent, xx)
		res.timeoutDelay = timeoutDelay
		res.resultHandler = null
		res.parentBuilder = this
		return res
	}

	fun handleResponseStanza(
		requestName: String? = null, handler: ResponseStanzaHandler<STT>
	): RequestConsumerBuilder<CSR, V, STT> {
		this.responseStanzaHandler = handler
		if (requestName != null) this.requestName = requestName
		return this
	}

	fun response(requestName: String? = null, handler: ResultHandler<V>): RequestConsumerBuilder<CSR, V, STT> {
		this.resultHandler = handler
		if (requestName != null) this.requestName = requestName
		return this
	}

	fun consume(handler: (CSR) -> Unit): RequestConsumerBuilder<CSR, V, STT> {
		this.publisher.observers.add(handler)
		return this
	}

	fun timeToLive(time: Long): RequestConsumerBuilder<CSR, V, STT> {
		timeoutDelay = time
		return this
	}

	fun send(): Request<V, STT> {
		val req = build()
		halcyon.writer.write(req)
		return req
	}

	fun name(requestName: String): RequestConsumerBuilder<CSR, V, STT> {
		this.requestName = requestName
		return this
	}

}