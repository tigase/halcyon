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
package tigase.halcyon.core

import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.connector.ConnectorStateChangeEvent
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SentXMLElementEvent
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventHandler
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.requests.RequestBuilderFactory
import tigase.halcyon.core.requests.RequestsManager
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.SessionController
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.*
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import tigase.halcyon.core.xmpp.stanzas.IQ

data class HalcyonStateChangeEvent(val oldState: AbstractHalcyon.State, val newState: AbstractHalcyon.State) :
	Event(TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.HalcyonStateChangeEvent"
	}
}

data class TickEvent(val counter: Long, val timestamp: Long) : Event(TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.TickEvent"
	}
}

abstract class AbstractHalcyon : Context, PacketWriter {

	var running: Boolean = false
		private set

	private val log = Logger("tigase.halcyon.core.AbstractHalcyon")

	enum class State {
		Connecting,
		Connected,
		Disconnecting,
		Disconnected,
		Stopped
	}

	protected var connector: AbstractConnector? = null
	protected var sessionController: SessionController? = null

	var autoReconnect: Boolean = true

	override val request = RequestBuilderFactory(this)

	private var tickCounter: Long = 0

	final override val sessionObject: SessionObject = SessionObject()
	final override val eventBus: tigase.halcyon.core.eventbus.EventBus =
		tigase.halcyon.core.eventbus.EventBus(sessionObject)
	override val writer: PacketWriter
		get() = this
	final override val modules: tigase.halcyon.core.modules.ModulesManager =
		tigase.halcyon.core.modules.ModulesManager()
	val requestsManager: RequestsManager = RequestsManager()
	val configuration: ConfigurationBuilder = ConfigurationBuilder(this)
	private val executor = tigase.halcyon.core.excutor.Executor()

	var state = State.Stopped
		internal set(value) {
			val old = field
			field = value
			if (old != field) eventBus.fire(HalcyonStateChangeEvent(old, field))
		}

	init {
		sessionObject.eventBus = eventBus
		modules.context = this
		eventBus.register<ReceivedXMLElementEvent>(tigase.halcyon.core.connector.ReceivedXMLElementEvent.TYPE) { event ->
			processReceivedXmlElement(event.element)
		}

		eventBus.register(SessionController.SessionControllerEvents.TYPE, ::onSessionControllerEvent)

		eventBus.register<TickEvent>(TickEvent.TYPE) { requestsManager.findOutdated() }

		modules.register(PresenceModule())
		modules.register(PubSubModule())
		modules.register(MessageModule())
		modules.register(StreamManagementModule())
		modules.register(SASLModule())
		modules.register(BindModule())
		modules.register(PingModule())
		modules.register(StreamErrorModule())
		modules.register(StreamFeaturesModule())
	}

	protected open fun onSessionControllerEvent(event: SessionController.SessionControllerEvents) {
		when (event) {
			is SessionController.SessionControllerEvents.ErrorStop, is SessionController.SessionControllerEvents.ErrorReconnect -> processControllerErrorEvent(
				event
			)
			is SessionController.SessionControllerEvents.Successful -> state = State.Connected
		}
	}

	private fun processControllerErrorEvent(event: SessionController.SessionControllerEvents) {
		if (event is SessionController.SessionControllerEvents.ErrorReconnect && (this.autoReconnect || event.force)) {
			state = State.Disconnected
			stopConnector {
				reconnect(event.immediately)
			}
		} else {
			disconnect()
		}
	}

	abstract fun reconnect(immediately: Boolean = false)

	protected fun processReceivedXmlElement(element: Element) {
		val handled = requestsManager.findAndExecute(element)
		if (handled && element.name == IQ.NAME) return

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
				if (log.isLoggable(tigase.halcyon.core.logger.Level.FINEST)) log.log(
					tigase.halcyon.core.logger.Level.FINEST,
					"Error ${e.condition} during processing stanza " + element.getAsString(),
					e
				)
				sendErrorBack(element, e)
			} catch (e: Exception) {
				if (log.isLoggable(tigase.halcyon.core.logger.Level.FINEST)) log.log(
					tigase.halcyon.core.logger.Level.FINEST,
					"Problem on processing element " + element.getAsString(),
					e
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
		eventBus.fire(tigase.halcyon.core.TickEvent(++tickCounter, tigase.halcyon.core.currentTimestamp()))
	}

	protected fun getConnectorState(): tigase.halcyon.core.connector.State =
		this.connector?.state ?: tigase.halcyon.core.connector.State.Disconnected

	override fun writeDirectly(stanza: Element) {
		val c = this.connector ?: throw HalcyonException("Connector is not initialized")
		if (c.state != tigase.halcyon.core.connector.State.Connected) throw HalcyonException("Connector is not connected")
		c.send(stanza.getAsString())
		eventBus.fire(SentXMLElementEvent(stanza, null))
	}

	override fun write(request: Request<*, *>) {
		val c = this.connector ?: throw HalcyonException("Connector is not initialized")
		if (c.state != tigase.halcyon.core.connector.State.Connected) throw HalcyonException("Connector is not connected")
		requestsManager.register(request)
		c.send(request.stanza.getAsString())
		if (!StreamManagementModule.isAckEnable(sessionObject)) {
			request.isSent = true
		}
		eventBus.fire(SentXMLElementEvent(request.stanza, request))
	}

	protected open fun onConnecting() {}

	protected open fun onDisconnecting() {}

	fun <T : XmppModule> getModule(type: String): T? = modules.getModuleOrNull<T>(type)

	protected fun startConnector() {
		if (running) {
			log.fine("Starting connector")

			stopConnector()

			connector = createConnector()
			sessionController = connector!!.createSessionController()

			sessionController!!.start()
			connector!!.start()
		} else throw HalcyonException("Client is not running")
	}

	protected fun stopConnector(doAfterDisconnected: (() -> Unit)? = null) {
		log.fine("Stopping connector${if (doAfterDisconnected != null) " (with action after disconnect)" else ""}")
		if (doAfterDisconnected != null) connector?.let {
			waitForDisconnect(it, doAfterDisconnected)
		}
		sessionController?.stop()
		sessionController = null
		connector?.stop()
		connector = null
	}

	protected fun waitForDisconnect(connector: AbstractConnector?, handler: () -> Unit) {
		log.finer("Waiting for disconnection")
		if (connector == null) {
			log.finest("No connector. Calling handler.")
			handler.invoke()
		} else {
			var fired = false
			val h: EventHandler<ConnectorStateChangeEvent> =
				object : EventHandler<ConnectorStateChangeEvent> {
					override fun onEvent(event: ConnectorStateChangeEvent) {
						if (!fired && event.newState == tigase.halcyon.core.connector.State.Disconnected) {
							connector.context.eventBus.unregister(this)
							fired = true
							log.finest("State changed. Calling handler.")
							handler.invoke()
						}
					}
				}
			try {
				connector.context.eventBus.register(ConnectorStateChangeEvent.TYPE, h)
				if (!fired && connector.state == tigase.halcyon.core.connector.State.Disconnected) {
					connector.context.eventBus.unregister(h)
					fired = true
					log.finest("State is Disconnected already. Calling handler.")
					handler.invoke()
				}
			} finally {
			}
		}
	}

	fun connect() {
		sessionObject.clear(SessionObject.Scope.Session)
		this.running = true
		modules.initModules()
		log.info("Connecting")
		state = State.Connecting
		onConnecting()
		try {
			startConnector()
		} catch (e: Exception) {
			state = State.Stopped
			throw e
		}
	}

	fun disconnect() {
		try {
			this.running = false
			log.info("Disconnecting")

			modules.getModuleOrNull<StreamManagementModule>(StreamManagementModule.TYPE)?.let {
				if (StreamManagementModule.isAckEnable(sessionObject) && getConnectorState() == tigase.halcyon.core.connector.State.Connected) {
					it.sendAck(true)
				}
			}

			state = State.Disconnecting
			onDisconnecting()
			stopConnector()
		} finally {
			sessionObject.clear(SessionObject.Scope.Session)
			state = State.Stopped
		}
	}
}