package org.tigase.jaxmpp.core

import org.tigase.jaxmpp.Configurator
import org.tigase.jaxmpp.core.connector.AbstractConnector
import org.tigase.jaxmpp.core.connector.ReceivedXMLElementEvent
import org.tigase.jaxmpp.core.connector.SentXMLElementEvent
import org.tigase.jaxmpp.core.eventbus.Event
import org.tigase.jaxmpp.core.eventbus.EventBus
import org.tigase.jaxmpp.core.exceptions.JaXMPPException
import org.tigase.jaxmpp.core.logger.Level
import org.tigase.jaxmpp.core.logger.Logger
import org.tigase.jaxmpp.core.modules.ModulesManager
import org.tigase.jaxmpp.core.requests.Request
import org.tigase.jaxmpp.core.requests.RequestsManager
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xml.stanza
import org.tigase.jaxmpp.core.xmpp.SessionController
import org.tigase.jaxmpp.core.xmpp.modules.BindModule
import org.tigase.jaxmpp.core.xmpp.modules.PingModule
import org.tigase.jaxmpp.core.xmpp.modules.StreamErrorModule
import org.tigase.jaxmpp.core.xmpp.modules.StreamFeaturesModule
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

	var state = State.Disconnected
		internal set(value) {
			val old = field
			field = value
			eventBus.fire(JaXMPPStateChangeEvent(old, field))
		}

	init {
		modules.context = this
		eventBus.register<ReceivedXMLElementEvent>(
			ReceivedXMLElementEvent.TYPE,
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
			sendErrorBack(element)
			return
		}

		modules.forEach {
			try {
				it.process(element)
			} catch (e: Exception) {
				log.log(Level.WARNING, "Problem on processing element " + element.getAsString(), e)
			}
		}
	}

	fun sendErrorBack(element: Element) {
		when (element.name) {
			"iq", "presence", "message" -> {
				TODO("Dorobic wysylanie bledów")
			}
			else -> {
				writeDirectly(stanza("stream:error") {
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