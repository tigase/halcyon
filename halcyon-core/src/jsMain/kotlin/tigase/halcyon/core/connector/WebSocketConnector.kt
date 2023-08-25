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
package tigase.halcyon.core.connector

import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event
import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.configuration.declaredUserJID
import tigase.halcyon.core.excutor.TickExecutor
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.parser.StreamParser
import tigase.halcyon.core.xmpp.modules.discoaltconn.AlternativeConnectionMethodModule
import tigase.halcyon.core.xmpp.modules.discoaltconn.HostLink
import kotlin.time.Duration.Companion.seconds

class WebSocketConnectionErrorEvent(@Suppress("unused") val description: String) : ConnectionErrorEvent()

class WebSocketConnector(halcyon: Halcyon) : AbstractConnector(halcyon) {

	private val log = LoggerFactory.logger("tigase.halcyon.core.connector.WebSocketConnector")

	private var config: WebSocketConnectorConfig = halcyon.config.connection as WebSocketConnectorConfig

	private val whitespacePingExecutor = TickExecutor(halcyon.eventBus, 25.seconds) { onTick() }

	private val parser = object : StreamParser() {
		private fun logReceivedStanza(element: Element) {
			when {
				log.isLoggable(Level.FINEST) -> log.finest("Received element ${element.getAsString()}")
				log.isLoggable(Level.FINER) -> log.finer(
					"Received element ${
						element.getAsString(deep = 3, showValue = false)
					}"
				)

				log.isLoggable(Level.FINE) -> log.fine(
					"Received element ${
						element.getAsString(deep = 2, showValue = false)
					}"
				)
			}
		}

		override fun onNextElement(element: Element) {
			logReceivedStanza(element)
			halcyon.eventBus.fire(ReceivedXMLElementEvent(element))
		}

		override fun onStreamClosed() {
			log.finest { "Stream closed" }
			halcyon.eventBus.fire(StreamTerminatedEvent())
		}

		override fun onStreamStarted(attrs: Map<String, String>) {
			log.finest { "Stream started: $attrs" }
			halcyon.eventBus.fire(StreamStartedEvent(attrs))
		}

		override fun onParseError(errorMessage: String) {
			log.finest { "Parse error: $errorMessage" }
			halcyon.eventBus.fire(ParseErrorEvent(errorMessage))
		}
	}

	private lateinit var ws: WebSocket

	override fun createSessionController(): SessionController = WebSocketSessionController(halcyon, this)

	override fun send(data: CharSequence) {
		log.finest { "Sending: $data" }
		try {
			this.ws.send(data.toString())
		} catch (e: Throwable) {
			log.warning(e) { "Cannot send data." }
			halcyon.eventBus.fire(WebSocketConnectionErrorEvent("Cannot send data"))
			throw e
		}
	}

	override fun start() {
		state = State.Connecting
		log.fine { "Starting WebSocket connector" }

		createSocket { sckt ->
			log.finest { "Created WS: $sckt" }

			this.ws = sckt
			ws.onmessage = this::onSocketMessageEvent
			ws.onerror = this::onSocketError
			ws.onopen = this::onSocketOpen
			ws.onclose = this::onSocketClose
		}
	}

	private fun createSocket(completionHandler: (WebSocket) -> Unit) {
		resolveTarget { url ->
			if (!config.allowUnsecureConnection && url.startsWith("ws:")) {
				throw ConnectorException("Unsecure connection is not allowed.")
			}
			try {
				log.info { "Opening WebSocket connection to $url" }
				val s = WebSocket(url, "xmpp")
				completionHandler(s)
				return@resolveTarget
			} catch (e: Throwable) {
				log.fine { "Websocket $url is unreachable." }
				throw ConnectorException(e)
			}
		}
	}

	private fun resolveTarget(completionHandler: (String) -> Unit) {
		config.webSocketUrl?.let {
			log.fine("WebSocket URL is declared: $it")
			completionHandler(it)
			return
		}

		halcyon.getModuleOrNull(AlternativeConnectionMethodModule)?.let { md ->
			log.fine("Checking alternative connection methods...")
			md.discovery(config.domain) {
				log.fine("Found connection methods: $it")
				it.searchForProtocol("wss:")?.let { url ->
					completionHandler(url)
					return@discovery
				}
				if (config.allowUnsecureConnection) {
					it.searchForProtocol("ws:")?.let { url ->
						completionHandler(url)
						return@discovery
					}
				}
				completionHandler("wss://${config.domain}:5291/")
			}
			return
		}

		completionHandler("wss://${config.domain}:5291/")
	}

	private fun onSocketClose(event: Event): dynamic {
		log.fine { "Socket is closed: $event" }
		if (state == State.Connected) halcyon.eventBus.fire(WebSocketConnectionErrorEvent("Socket unexpectedly disconnected."))
		state = State.Disconnected
		eventsEnabled = false
		return true
	}

	private fun onSocketOpen(event: Event): dynamic {
		log.fine { "Socket opened $event" }
		state = State.Connected
		whitespacePingExecutor.start()

		restartStream()

		return true
	}

	private fun onSocketError(event: Event): dynamic {
		log.warning { "Socket error: $event" }
		halcyon.eventBus.fire(WebSocketConnectionErrorEvent("Unknown error"))
		state = when (state) {
			State.Connecting -> State.Disconnected
			State.Connected -> State.Disconnecting
			State.Disconnecting -> State.Disconnected
			State.Disconnected -> State.Disconnected
		}
		if (state == State.Disconnected) eventsEnabled = false
		return true
	}

	private fun onSocketMessageEvent(event: MessageEvent): dynamic {
		log.fine { "Received: ${event.data}" }
		parser.parse(event.data.toString())

		return true
	}

	override fun stop() {
		log.info { "Stopping WebSocket connector" }
		whitespacePingExecutor.stop()
		if (state == State.Connected) closeStream()
		state = State.Disconnecting
		this.ws.close()
	}

	private fun closeStream() {
		send("</stream:stream>")
	}

	fun restartStream() {
		log.finest { "Send new stream" }
		val userJid = halcyon.config.declaredUserJID
		val domain = (halcyon.config.connection as WebSocketConnectorConfig).domain

		val sb = buildString {
			append("<stream:stream ")
			append("xmlns='jabber:client' ")
			append("xmlns:stream='http://etherx.jabber.org/streams' ")
			append("version='1.0' ")
			if (userJid != null) append("from='${userJid}' ")
			append("to='${domain}' ")
			append(">")
		}

		send(sb)
	}

	private fun onTick() {
		if (state == State.Connected) {
			log.finer { "Whitespace ping" }
			this.ws.send("")
		}
	}

}

private fun List<HostLink>.searchForProtocol(protocolPrefix: String) =
	this.filter { it.rel == "urn:xmpp:alt-connections:websocket" }.filter { it.href.startsWith(protocolPrefix) }
		.map { it.href }.firstOrNull()