/*
 * halcyon-core
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

import kotlinx.datetime.Clock
import tigase.halcyon.core.Context
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.ElementImpl
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.stanzas.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

//typealias ResultHandler<V> = (Result<V>) -> Unit
typealias ResponseStanzaHandler<STT> = (Request<*, STT>, STT?) -> Unit

class XMPPError(val response: Stanza<*>?, val error: ErrorCondition, val description: String?) : Exception(description)

typealias RHandler<V> = (Result<V>) -> Unit

class ResultHandler<V> {

	private val handlers: MutableList<RHandler<V>> = mutableListOf()

	fun add(handler: RHandler<V>) {
		this.handlers.add(handler)
	}

	fun invoke(tmp: Result<V>) {
		handlers.forEach {
			it.invoke(tmp)
		}
	}

}

class RequestBuilderFactory(private val context: Context) {

	fun iq(stanza: Element): RequestBuilder<IQ, IQ> = RequestBuilder(halcyon = context, element = stanza) { it as IQ }

	fun iq(init: IQNode.() -> Unit): RequestBuilder<IQ, IQ> {
		val n = IQNode(IQ(ElementImpl(IQ.NAME)))
		n.init()
		n.id()
		val stanza = n.element as IQ
		return RequestBuilder(halcyon = context, element = stanza) { it as IQ }
	}

	fun presence(stanza: Element): RequestBuilder<Unit, Presence> =
		RequestBuilder(halcyon = context, element = stanza, callHandlerOnSent = true) { Unit }

	fun presence(init: PresenceNode.() -> Unit): RequestBuilder<Unit, Presence> {
		val n = PresenceNode(Presence(ElementImpl(Presence.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Presence
		return RequestBuilder(halcyon = context, element = stanza, callHandlerOnSent = true) { Unit }
	}

	fun message(stanza: Element, writeDirectly: Boolean = false): RequestBuilder<Unit, Message> = RequestBuilder(
		halcyon = context, element = stanza, callHandlerOnSent = true
	) { Unit }

	fun message(writeDirectly: Boolean = false, init: MessageNode.() -> Unit): RequestBuilder<Unit, Message> {
		val n = MessageNode(Message(ElementImpl(Message.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Message
		return RequestBuilder(
			halcyon = context, element = stanza, writeDirectly = writeDirectly, callHandlerOnSent = true
		) { Unit }
	}

}

class RequestBuilder<V, STT : Stanza<*>>(
	private val halcyon: Context,
	private val element: Element,
	private val writeDirectly: Boolean = false,
	private val callHandlerOnSent: Boolean = false,
	private val transform: (value: Any) -> V
) {

	private var requestName: String? = null

	private var parentBuilder: RequestBuilder<*, STT>? = null

	private var timeoutDelay = 30.toDuration(DurationUnit.SECONDS)

	private var resultHandler: ResultHandler<V>? = null

	private var responseStanzaHandler: ResponseStanzaHandler<STT>? = null

	fun build(): Request<V, STT> {
		val stanza = wrap<STT>(halcyon.modules.processSendInterceptors(element))
		return Request(
			stanza.to,
			stanza.id!!,
			Clock.System.now(),
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
		check(!writeDirectly) { "Mapping cannot be added to directly writable request." }
		val res =
			RequestBuilder<R, STT>(halcyon, element, writeDirectly, callHandlerOnSent, transform as (((Any) -> R)))
		res.timeoutDelay = timeoutDelay
		res.resultHandler = null
		res.parentBuilder = this
		return res
	}

	fun handleResponseStanza(name: String? = null, handler: ResponseStanzaHandler<STT>): RequestBuilder<V, STT> {
		check(!writeDirectly) { "Response handler cannot be added to directly writable request." }
		this.responseStanzaHandler = handler
		if (requestName != null) this.requestName = requestName
		return this
	}

	fun response(requestName: String? = null, handler: RHandler<V>): RequestBuilder<V, STT> {
		check(!writeDirectly) { "Response handler cannot be added to directly writable request." }
		if (this.resultHandler == null) this.resultHandler = ResultHandler()
		this.resultHandler?.add(handler)
		if (requestName != null) this.requestName = requestName
		return this
	}

	fun timeToLive(time: Long): RequestBuilder<V, STT> {
		timeoutDelay = time.toDuration(DurationUnit.MILLISECONDS)
		return this
	}

	fun timeToLive(duration: Duration): RequestBuilder<V, STT> {
		timeoutDelay = duration
		return this
	}

	fun send(): Request<V, STT> {
		val req = build()
		if (writeDirectly) halcyon.writer.writeDirectly(req.stanza)
		else halcyon.writer.write(req)
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
	private val writeDirectly: Boolean = false,
	private val callHandlerOnSent: Boolean = false,
	private val transform: (value: Any) -> V
) {

	private var requestName: String? = null

	internal val publisher = ConsumerPublisher<CSR>()

	private var parentBuilder: RequestConsumerBuilder<CSR, *, STT>? = null

	private var timeoutDelay = 30.toDuration(DurationUnit.SECONDS)

	private var resultHandler: ResultHandler<V>? = null

	private var responseStanzaHandler: ResponseStanzaHandler<STT>? = null

	fun build(): Request<V, STT> {
		val stanza = wrap<STT>(halcyon.modules.processSendInterceptors(element))
		return Request(
			stanza.to,
			stanza.id!!,
			Clock.System.now(),
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
		val res = RequestConsumerBuilder<CSR, R, STT>(halcyon, element, writeDirectly, callHandlerOnSent, xx)
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

	fun response(requestName: String? = null, handler: RHandler<V>): RequestConsumerBuilder<CSR, V, STT> {
		if (this.resultHandler == null) this.resultHandler = ResultHandler()
		this.resultHandler?.add(handler)
		if (requestName != null) this.requestName = requestName
		return this
	}

	fun consume(handler: (CSR) -> Unit): RequestConsumerBuilder<CSR, V, STT> {
		this.publisher.observers.add(handler)
		return this
	}

	fun timeToLive(time: Long): RequestConsumerBuilder<CSR, V, STT> {
		timeoutDelay = time.toDuration(DurationUnit.MILLISECONDS)
		return this
	}

	fun timeToLive(duration: Duration): RequestConsumerBuilder<CSR, V, STT> {
		timeoutDelay = duration
		return this
	}

	fun send(): Request<V, STT> {
		val req = build()
		if (writeDirectly) halcyon.writer.writeDirectly(req.stanza)
		else halcyon.writer.write(req)
		return req
	}

	fun name(requestName: String): RequestConsumerBuilder<CSR, V, STT> {
		this.requestName = requestName
		return this
	}

}