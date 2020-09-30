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
typealias ResponseStanzaHandler<STT> = (AbstractRequest<*, STT>, STT?) -> Unit

class XMPPError(val response: Stanza<*>?, val error: ErrorCondition, val description: String?) : Exception()

class RequestBuilderFactory(private val halcyon: AbstractHalcyon) {

	fun iq(stanza: Element): RequestBuilder<IQ, IQ> = RequestBuilder(halcyon, stanza) { it as IQ }
	fun iq(init: IQNode.() -> Unit): RequestBuilder<IQ, IQ> {
		val n = IQNode(IQ(ElementImpl(IQ.NAME)))
		n.init()
		n.id()
		val stanza = n.element as IQ
		return RequestBuilder<IQ, IQ>(halcyon, stanza) { it as IQ }
	}

	fun presence(stanza: Element): RequestBuilder<Unit, Presence> = RequestBuilder(halcyon, stanza) { Unit }

	fun presence(init: PresenceNode.() -> Unit): RequestBuilder<Unit, Presence> {
		val n = PresenceNode(Presence(ElementImpl(Presence.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Presence
		return RequestBuilder(halcyon, stanza) { Unit }
	}

	fun message(stanza: Element): RequestBuilder<Unit, Message> = RequestBuilder(halcyon, stanza) { Unit }

	fun message(init: MessageNode.() -> Unit): RequestBuilder<Unit, Message> {
		val n = MessageNode(Message(ElementImpl(Message.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Message
		return RequestBuilder(halcyon, stanza) { Unit }
	}

}

class Request<V, STT : Stanza<*>>(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	stanza: STT,
	timeoutDelay: Long,
	handler: ResultHandler<V>?,
	transform: (value: Any) -> V,
	parentRequest: Request<*, STT>? = null
) : AbstractRequest<V, STT>(jid, id, creationTimestamp, stanza, timeoutDelay, handler, transform, parentRequest)

class RequestBuilder<V, STT : Stanza<*>>(
	private val halcyon: AbstractHalcyon, private val element: Element, private val transform: (value: Any) -> V
) {

	private var parentBuilder: RequestBuilder<*, STT>? = null

	private var timeoutDelay: Long = 30000

	private var resultHandler: ResultHandler<V>? = null

	private var responseStanzaHandler: ResponseStanzaHandler<STT>? = null

	fun build(): Request<V, STT> {
		val stanza = wrap<STT>(halcyon.modules.processSendInterceptors(element))
		return Request<V, STT>(stanza.to,
							   stanza.id!!,
							   currentTimestamp(),
							   stanza,
							   timeoutDelay,
							   resultHandler,
							   transform,
							   parentBuilder?.build()).apply {
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

	fun <R : Any> map(transform: (value: V) -> R): RequestBuilder<R, STT> {
		val xx: ((Any) -> R) = transform as (((Any) -> R))
		val res = RequestBuilder<R, STT>(halcyon, element, xx)
		res.timeoutDelay = timeoutDelay
		res.resultHandler = null
		res.parentBuilder = this
		return res
	}

	fun handleResponseStanza(handler: ResponseStanzaHandler<STT>): RequestBuilder<V, STT> {
		this.responseStanzaHandler = handler
		return this
	}

	fun response(handler: ResultHandler<V>): RequestBuilder<V, STT> {
		this.resultHandler = handler
		return this
	}

	fun timeToLive(time: Long): RequestBuilder<V, STT> {
		timeoutDelay = time
		return this
	}

	fun send(): Request<V, STT> {
		val req = build()
		halcyon.write(req)
		return req
	}

}