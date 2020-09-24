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
package tigase.halcyon.core.request2

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.requests.RequestErrorException
import tigase.halcyon.core.requests.RequestNotCompletedException
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.ElementImpl
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.stanzas.*

typealias ResultHandler<V> = (Result<V>) -> Unit

class XMPPError(val response: Stanza<*>?, val error: ErrorCondition, val description: String?) : Exception()

class RequestBuilderFactory(private val halcyon: AbstractHalcyon) {

	fun iq(stanza: Element): RequestBuilder<Unit, ErrorCondition, IQ> = RequestBuilder(halcyon, stanza) { Unit }
	fun iq(init: IQNode.() -> Unit): RequestBuilder<Unit, ErrorCondition, IQ> {
		val n = IQNode(IQ(ElementImpl(IQ.NAME)))
		n.init()
		n.id()
		val stanza = n.element as IQ
		return RequestBuilder(halcyon, stanza) { Unit }
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

class Request2<V, E, STT : Stanza<*>>(
	jid: JID?,
	id: String,
	creationTimestamp: Long,
	stanza: STT,
	timeoutDelay: Long,
	private val handler: ResultHandler<V>?,
	private val transform: (value: STT) -> V
) : Request<V, STT>(jid, id, creationTimestamp, stanza, timeoutDelay) {

	override fun createRequestNotCompletedException(): RequestNotCompletedException = RequestNotCompletedException(this)

	override fun createRequestErrorException(error: ErrorCondition, text: String?): RequestErrorException =
		RequestErrorException(this, error, text)

	override fun callHandlers() {
		val result = if (isTimeout) {
			Result.failure(XMPPError(null, ErrorCondition.RemoteServerTimeout, null))
		} else {
			val type = response!!.attributes["type"]
			if (type == "result") {
				Result.success(response).map { st -> transform.invoke(st!!) }
			} else if (type == "error") {
				val e = findCondition(response!!)
				Result.failure(XMPPError(response, e.condition, e.message))
			} else {
				Result.failure(XMPPError(response, ErrorCondition.UnexpectedRequest, null))
			}

		}
		handler?.invoke(result)
	}
}

class RequestBuilder<V, ERR, STT : Stanza<*>>(
	private val halcyon: AbstractHalcyon, private val element: Element, private val transform: (value: STT) -> V
) {

	private var timeoutDelay: Long = 30000

	private var handler: ResultHandler<V>? = null

	fun build(): Request2<V, ERR, STT> {
		val stanza = wrap<STT>(halcyon.modules.processSendInterceptors(element))
		return Request2<V, ERR, STT>(
			stanza.to, stanza.id!!, currentTimestamp(), stanza, timeoutDelay, handler, transform
		)
	}

	fun <R : Any> map(transform: (value: STT) -> R): RequestBuilder<R, ERR, STT> {
		val res = RequestBuilder<R, ERR, STT>(halcyon, element, transform)
		res.timeoutDelay = timeoutDelay
		res.handler = null
		return res
	}

	fun response(handler: ResultHandler<V>): RequestBuilder<V, ERR, STT> {
		this.handler = handler
		return this
	}

	fun timeToLive(time: Long): RequestBuilder<V, ERR, STT> {
		timeoutDelay = time
		return this
	}

	fun send(): Request2<V, ERR, STT> {
		val req = build()
		halcyon.write(req)
		return req
	}

}