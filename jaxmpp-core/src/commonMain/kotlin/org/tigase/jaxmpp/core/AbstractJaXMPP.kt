package org.tigase.jaxmpp.core

import org.tigase.jaxmpp.Configurator
import org.tigase.jaxmpp.core.connector.AbstractConnector
import org.tigase.jaxmpp.core.connector.ReceivedXMLElementEvent
import org.tigase.jaxmpp.core.connector.SentXMLElementEvent
import org.tigase.jaxmpp.core.eventbus.Event
import org.tigase.jaxmpp.core.eventbus.EventBus
import org.tigase.jaxmpp.core.exceptions.JaXMPPException
import org.tigase.jaxmpp.core.excutor.Executor
import org.tigase.jaxmpp.core.logger.Level
import org.tigase.jaxmpp.core.logger.Logger
import org.tigase.jaxmpp.core.modules.ModulesManager
import org.tigase.jaxmpp.core.requests.Request
import org.tigase.jaxmpp.core.requests.RequestsManager
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xml.element
import org.tigase.jaxmpp.core.xmpp.ErrorCondition
import org.tigase.jaxmpp.core.xmpp.SessionController
import org.tigase.jaxmpp.core.xmpp.XMPPException
import org.tigase.jaxmpp.core.xmpp.modules.*
import org.tigase.jaxmpp.core.xmpp.modules.auth.SaslModule
import org.tigase.jaxmpp.core.xmpp.modules.sm.StreamManagementModule

data class JaXMPPStateChangeEvent(val oldState: AbstractJaXMPP.State, val newState: AbstractJaXMPP.State) : Event(
	TYPE
) {

	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.JaXMPPStateChangeEvent"
	}
}

data class TickEvent(val counter: Long, val timestamp: Long) : Event(TYPE) {
	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.TickEvent"
	}
}

abstract class AbstractJaXMPP : Context, PacketWriter {

	private val log = Logger("org.tigase.jaxmpp.core.AbstractJaXMPP")

	enum class State {
		Connecting,
		Connected,
		Disconnecting,
		Disconnected
	}

	protected var connector: AbstractConnector? = null
	protected var sessionController: SessionController? = null

	private var tickCounter: Long = 0;

	final override val sessionObject: SessionObject = SessionObject()
	final override val eventBus: EventBus = EventBus(sessionObject)
	override val writer: PacketWriter
		get() = this
	final override val modules: ModulesManager = ModulesManager()
	val requestsManager: RequestsManager = RequestsManager()
	val configurator: Configurator = Configurator(sessionObject)
	private val executor = Executor()

	var state = State.Disconnected
		internal set(value) {
			val old = field
			field = value
			eventBus.fire(JaXMPPStateChangeEvent(old, field))
		}

	init {
		modules.context = this
		eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE,
												   handler = { _, event -> processReceivedXmlElement(event.element) })

		eventBus.register<SessionController.StopEverythingEvent>(
			SessionController.StopEverythingEvent.TYPE
		) { sessionObject, event -> disconnect() }

		modules.register(StreamErrorModule())
		modules.register(StreamFeaturesModule())
		modules.register(StreamManagementModule())
		modules.register(SaslModule())
		modules.register(BindModule())
		modules.register(PingModule())
	}

	protected fun processReceivedXmlElement(element: Element) {
		val handled = requestsManager.findAndExecute(element);
		if (handled) return

		val modules = modules.getModulesFor(element)
		if (modules.isEmpty()) {
			log.fine("Unsupported stanza: " + element.getAsString())
			sendErrorBack(element, XMPPException(ErrorCondition.FeatureNotImplemented))
			return
		}

		executor.execute {
			try {
				modules.forEach {
					it.process(element)
				}
			} catch (e: XMPPException) {
				if (log.isLoggable(Level.FINEST)) log.log(
					Level.FINEST, "Error ${e.condition} during processing stanza " + element.getAsString(), e
				)
				sendErrorBack(element, e)
			} catch (e: Exception) {
				if (log.isLoggable(Level.FINEST)) log.log(
					Level.FINEST, "Problem on processing element " + element.getAsString(), e
				)
				sendErrorBack(element, e)
			}
		}
	}

	private fun createError(element: Element, caught: Exception): Element? {
		if (caught is XMPPException) {
			return createError(element, caught.condition, caught.message)
		} else {
			return null
		}
	}

	private fun createError(element: Element, errorCondition: ErrorCondition, msg: String?): Element {
		val resp = element(element.name) {
			attribute("type", "error")
			element.attributes["id"]?.apply {
				attribute("id", this)
			}
			element.attributes["from"]?.apply {
				attribute("to", this)
			}

			"error"{
				errorCondition.type?.let {
					attribute("type", it)
				}
				errorCondition.errorCode?.let {
					attribute("code", it.toString())
				}

				errorCondition.elementName {
					attribute("xmlns", XMPPException.XMLNS)
				}

				msg?.let {
					"text"{
						attribute("xmlns", XMPPException.XMLNS)
						+it
					}
				}

			}

		}
		return resp
	}

	private fun sendErrorBack(element: Element, exception: Exception) {
		when (element.name) {
			"iq", "presence", "message" -> {
				if (element.attributes["type"] == "error") {
					log.fine("Ignoring unexpected error response")
					return
				}
				createError(element, exception)?.apply {
					writeDirectly(this)
				}
			}
			else -> {
				writeDirectly(element("stream:error") {
					"unsupported-stanza-type"{
						xmlns = "urn:ietf:params:xml:ns:xmpp-streams"
					}
				})
				connector?.stop()
			}
		}
	}

	protected abstract fun createConnector(): AbstractConnector

	protected fun tick() {
		eventBus.fire(TickEvent(++tickCounter, currentTimestamp()))
	}

	override fun writeDirectly(element: Element) {
		if (this.connector == null) throw JaXMPPException("Connector is not initialized")
		connector!!.send(element.getAsString())
		eventBus.fire(SentXMLElementEvent(element, null))
	}

	override fun write(stanza: Element): Request {
		if (this.connector == null) throw JaXMPPException("Connector is not initialized")
		val request = requestsManager.create(stanza)
		connector!!.send(stanza.getAsString())
		eventBus.fire(SentXMLElementEvent(stanza, request))
		return request
	}

	fun connect() {
		modules.initModules()
		log.info("Connecting")
		state = State.Connecting
		try {
			connector = createConnector()
			sessionController = connector!!.createSessionController()

			sessionController!!.start()
			connector!!.start()
		} catch (e: Exception) {
			state = State.Disconnected
			throw e
		}
	}

	fun disconnect() {
		log.info("Disconnecting")
		state = State.Disconnecting
		try {
			sessionController?.stop()
			connector?.stop()
		} finally {
			state = State.Disconnected
		}
	}

}