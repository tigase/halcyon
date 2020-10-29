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
package tigase.halcyon.core.connector

import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event
import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.excutor.TickExecutor
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.parser.StreamParser
import tigase.halcyon.core.xmpp.SessionController

class WebSocketConnectionErrorEvent(description: String) : ConnectionErrorEvent()

class WebSocketConnector(halcyon: Halcyon) : AbstractConnector(halcyon) {

	private val log = LoggerFactory.logger("tigase.halcyon.core.connector.WebSocketConnector")

	private var config: WebSocketConnectorConfig = halcyon.config.connectorConfig as WebSocketConnectorConfig

	private val whitespacePingExecutor = TickExecutor(halcyon.eventBus, 25000) { onTick() }

	private val parser = object : StreamParser() {
		private fun logReceivedStanza(element: Element) {
			when {
				log.isLoggable(Level.FINEST) -> log.finest("Received element ${element.getAsString()}")
				log.isLoggable(Level.FINER) -> log.finer(
					"Received element ${element.getAsString(deep = 3, showValue = false)}"
				)
				log.isLoggable(Level.FINE) -> log.fine(
					"Received element ${element.getAsString(deep = 2, showValue = false)}"
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

	private fun getDomain(): String {
		val userJid = halcyon.config.userJID
		val domain = halcyon.config.domain
		return domain ?: userJid?.domain ?: throw HalcyonException("No domain is specified")
	}

	override fun start() {
		log.fine { "Starting WebSocket connector" }
		state = State.Connecting

		val url = config.webSocketUrl ?: "ws://${getDomain()}:5290/"

		log.finer { "Connecting to $url" }

		this.ws = WebSocket(url, "xmpp")

		log.finest { "Created WS: $ws" }

		ws.onmessage = this::onSocketMessageEvent
		ws.onerror = this::onSocketError
		ws.onopen = this::onSocketOpen
		ws.onclose = this::onSocketClose

	}

	private fun onSocketClose(event: Event): dynamic {
		log.fine { "Socket is closed: $event" }
		var oldState = state
		if (oldState == State.Connected) halcyon.eventBus.fire(WebSocketConnectionErrorEvent("Socket unexpectedly disconnected."))
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
		val userJid = halcyon.config.userJID

		val sb = buildString {
			append("<stream:stream ")
			append("xmlns='jabber:client' ")
			append("xmlns:stream='http://etherx.jabber.org/streams' ")
			append("version='1.0' ")
			if (userJid != null) append("from='${userJid}' ")
			append("to='${getDomain()}' ")
			append(">")
		}

		send(sb)
	}

	private fun onTick() {
		if (state == State.Connected) {
			log.fine { "Whitespace ping" }
			this.ws.send("")
		}
	}

}