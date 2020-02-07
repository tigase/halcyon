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
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.ElementImpl
import tigase.halcyon.core.xmpp.stanzas.*

class RequestBuilderFactory(private val halcyon: AbstractHalcyon) {

	fun iq(stanza: Element): IQRequestBuilder<Unit> = IQRequestBuilder(halcyon, stanza) { Unit }
	fun iq(init: IQNode.() -> Unit): IQRequestBuilder<Unit> {
		val n = IQNode(IQ(ElementImpl(IQ.NAME)))
		n.init()
		n.id()
		val stanza = n.element as IQ
		return IQRequestBuilder(halcyon, stanza) { Unit }
	}

	fun presence(stanza: Element): PresenceRequestBuilder = PresenceRequestBuilder(halcyon, stanza)
	fun presence(init: PresenceNode.() -> Unit): PresenceRequestBuilder {
		val n = PresenceNode(Presence(ElementImpl(Presence.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Presence
		return PresenceRequestBuilder(halcyon, stanza)
	}

	fun message(stanza: Element): MessageRequestBuilder = MessageRequestBuilder(halcyon, stanza)
	fun message(init: MessageNode.() -> Unit): MessageRequestBuilder {
		val n = MessageNode(Message(ElementImpl(Message.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Message
		return MessageRequestBuilder(halcyon, stanza)
	}

}

class IQRequestBuilder<V : Any>(
	private val halcyon: AbstractHalcyon,
	private val element: Element,
	private var resultConverter: ResultConverter<V>
) {

	private var timeoutDelay: Long = 30000
	private var handler: IQResponseResultHandler<V>? = null

	init {
		if (element.name != IQ.NAME) {
			throw HalcyonException("Stanza <iq/> is expected.")
		} else if (element.attributes["id"] == null) {
			throw HalcyonException("Stanza MUST have 'id' attribute.")
		}
	}

	fun handle(init: IQHandlerHelper<V>.() -> Unit): IQRequestBuilder<V> {
		val callback = IQHandlerHelper<V>()
		callback.init()
		this.handler = { result ->
			val rh = callback.responseHandler()
			when (result) {
				is IQResult.Success<V> -> rh.success(result.request, result.response, result.value)
				is IQResult.Error<V> -> rh.error(result.request, result.response, result.error, result.text)
			}
		}
		return this
	}

	fun response(handler: IQResponseHandler<V>): IQRequestBuilder<V> {
		this.handler = { result ->
			when (result) {
				is IQResult.Success<V> -> handler.success(result.request, result.response, result.value)
				is IQResult.Error<V> -> handler.error(
					result.request, result.response, result.error, result.text
				)
			}
		}
		return this
	}

	fun response(handler: IQResponseResultHandler<V>): IQRequestBuilder<V> {
		this.handler = handler
		return this
	}

	fun timeToLive(time: Long): IQRequestBuilder<V> {
		timeoutDelay = time
		return this
	}

	fun <R : Any> resultBuilder(resultConverter: ResultConverter<R>): IQRequestBuilder<R> {
		val result = IQRequestBuilder<R>(halcyon, element, resultConverter)
		result.timeoutDelay = this.timeoutDelay
		result.handler = null
		return result
	}

	fun build(): IQRequest<V> {
		val stanza = wrap<IQ>(halcyon.modules.processSendInterceptors(element))
		return IQRequest(
			stanza.to, stanza.attributes["id"]!!, currentTimestamp(), stanza, handler,
			resultConverter,
			timeoutDelay
		)
	}

	fun send(): IQRequest<V> {
		val req = build()
		halcyon.write(req)
		return req
	}

}

class PresenceRequestBuilder(private val halcyon: AbstractHalcyon, private val element: Element) {

	private var timeoutDelay: Long = 30000

	private var stanzaHandler: StanzaStatusHandler<PresenceRequest>? = null

	init {
		if (element.name != Presence.NAME) {
			throw HalcyonException("Stanza <presence/> is expected.")
		}
	}

	fun timeToLive(time: Long): PresenceRequestBuilder {
		timeoutDelay = time
		return this
	}

	fun result(handler: StanzaStatusHandler<PresenceRequest>): PresenceRequestBuilder {
		this.stanzaHandler = handler
		return this
	}

	fun build(): PresenceRequest {
		val stanza = wrap<Presence>(halcyon.modules.processSendInterceptors(element))
		return PresenceRequest(
			stanza.to, stanza.attributes["id"]!!, currentTimestamp(), stanza, stanzaHandler, timeoutDelay
		)
	}

	fun send(): PresenceRequest {
		val req = build()
		halcyon.write(req)
		return req
	}

}

class MessageRequestBuilder(private val halcyon: AbstractHalcyon, private val element: Element) {

	private var timeoutDelay: Long = 30000

	private var stanzaHandler: StanzaStatusHandler<MessageRequest>? = null

	init {
		if (element.name != Message.NAME) {
			throw HalcyonException("Stanza <message/> is expected.")
		}
	}

	fun timeToLive(time: Long): MessageRequestBuilder {
		timeoutDelay = time
		return this
	}

	fun result(handler: StanzaStatusHandler<MessageRequest>): MessageRequestBuilder {
		this.stanzaHandler = handler
		return this
	}

	fun build(): MessageRequest {
		val stanza = wrap<Message>(halcyon.modules.processSendInterceptors(element))
		return MessageRequest(
			stanza.to, stanza.attributes["id"]!!, currentTimestamp(), stanza, stanzaHandler, timeoutDelay
		)
	}

	fun send(): MessageRequest {
		val req = build()
		halcyon.write(req)
		return req
	}

}
