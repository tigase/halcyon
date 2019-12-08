package tigase.halcyon.core.requests

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.ElementImpl
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.stanzas.*

class RequestBuilderFactory(private val halcyon: AbstractHalcyon) {

	fun <V : Any> iq(stanza: Element): IQReqBuilder<V> = IQReqBuilder(halcyon, stanza)
	fun <V : Any> iq(init: IQNode.() -> Unit): IQReqBuilder<V> {
		val n = IQNode(IQ(ElementImpl(IQ.NAME)))
		n.init()
		n.id()
		val stanza = n.element as IQ
		return IQReqBuilder(halcyon, stanza)
	}

	fun presence(stanza: Element): PresenceReqBuilder = PresenceReqBuilder(halcyon, stanza)
	fun presence(init: PresenceNode.() -> Unit): PresenceReqBuilder {
		val n = PresenceNode(Presence(ElementImpl(Presence.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Presence
		return PresenceReqBuilder(halcyon, stanza)
	}

	fun message(stanza: Element): MessageReqBuilder = MessageReqBuilder(halcyon, stanza)
	fun message(init: MessageNode.() -> Unit): MessageReqBuilder {
		val n = MessageNode(Message(ElementImpl(Message.NAME)))
		n.init()
		n.id()
		val stanza = n.element as Message
		return MessageReqBuilder(halcyon, stanza)
	}

}

class IQReqBuilder<V : Any>(private val halcyon: AbstractHalcyon, private val element: Element) {

	private var resultConverter: ResultConverter<V>? = null
	private var timeoutDelay: Long = 30000

	init {
		if (element.name != IQ.NAME) {
			throw HalcyonException("Stanza <iq/> is expected.")
		} else if (element.attributes["id"] == null) {
			throw HalcyonException("Stanza MUST have 'id' attribute.")
		}
	}

	inner class ResponseResponseResult(private val handler: IQResponseResultHandler<V>) :
		IQResponseHandler<V> {

		override fun success(request: IQRequest<V>, response: Element, value: V?) {
			handler.invoke(request, response, Result.Success(response, value))
		}

		override fun error(request: IQRequest<V>, response: Element?, error: ErrorCondition, errorMessage: String?) {
			handler.invoke(request, response, Result.Error(response, error))
		}
	}

	private var handler: IQResponseHandler<V>? = null

	fun handle(init: IQHandlerHelper<V>.() -> Unit): IQReqBuilder<V> {
		val callback = IQHandlerHelper<V>()
		callback.init()
		this.handler = callback.responseHandler()
		return this
	}

	fun response(handler: IQResponseHandler<V>): IQReqBuilder<V> {
		this.handler = handler
		return this
	}

	fun response(handler: IQResponseResultHandler<V>): IQReqBuilder<V> {
		this.handler = ResponseResponseResult(handler)
		return this
	}

	fun timeToLive(time: Long): IQReqBuilder<V> {
		timeoutDelay = time
		return this
	}

	fun resultBuilder(resultConverter: ResultConverter<V>): IQReqBuilder<V> {
		this.resultConverter = resultConverter
		return this
	}

	fun build(): IQRequest<V> {
		val stanza = wrap<IQ>(element)
		return IQRequest(
			stanza.to,
			stanza.attributes["id"]!!,
			currentTimestamp(),
			stanza,
			handler,
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

class PresenceReqBuilder(private val halcyon: AbstractHalcyon, private val element: Element) {

	private var timeoutDelay: Long = 30000

	private var errorHandler: PresenceErrorHandler? = null

	init {
		if (element.name != Presence.NAME) {
			throw HalcyonException("Stanza <presence/> is expected.")
		}
	}

	fun timeToLive(time: Long): PresenceReqBuilder {
		timeoutDelay = time
		return this
	}

	fun error(handler: PresenceErrorHandler): PresenceReqBuilder {
		this.errorHandler = handler
		return this
	}

	fun build(): PresenceRequest {
		val stanza = wrap<Presence>(element)
		return PresenceRequest(
			stanza.to,
			stanza.attributes["id"]!!,
			currentTimestamp(),
			stanza,
			errorHandler,
			timeoutDelay
		)
	}

	fun send(): PresenceRequest {
		val req = build()
		halcyon.write(req)
		return req
	}

}

class MessageReqBuilder(private val halcyon: AbstractHalcyon, private val element: Element) {

	private var timeoutDelay: Long = 30000

	private var errorHandler: MessageErrorHandler? = null

	init {
		if (element.name != Message.NAME) {
			throw HalcyonException("Stanza <message/> is expected.")
		}
	}

	fun timeToLive(time: Long): MessageReqBuilder {
		timeoutDelay = time
		return this
	}

	fun error(handler: MessageErrorHandler): MessageReqBuilder {
		this.errorHandler = handler
		return this
	}

	fun build(): MessageRequest {
		val stanza = wrap<Message>(element)
		return MessageRequest(
			stanza.to,
			stanza.attributes["id"]!!,
			currentTimestamp(),
			stanza,
			errorHandler,
			timeoutDelay
		)
	}

	fun send(): MessageRequest {
		val req = build()
		halcyon.write(req)
		return req
	}

}
