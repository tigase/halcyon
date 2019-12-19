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
import tigase.halcyon.core.SessionObject
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.excutor.TickExecutor
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.parser.StreamParser
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.SessionController

class WebSocketConnectionErrorEvent(description: String) : ConnectionErrorEvent()

class WebSocketConnector(halcyon: Halcyon) : AbstractConnector(halcyon) {

	private val log = Logger("tigase.halcyon.core.connector.WebSocketConnector")

	private val whitespacePingExecutor = TickExecutor(halcyon.eventBus, 25000) { onTick() }

	private val parser = object : StreamParser() {
		override fun onNextElement(element: Element) {
			log.finest("Received element ${element.getAsString()}")
			halcyon.eventBus.fire(ReceivedXMLElementEvent(element))
		}

		override fun onStreamClosed() {
			log.finest("Stream closed")
			halcyon.eventBus.fire(StreamTerminatedEvent())
		}

		override fun onStreamStarted(attrs: Map<String, String>) {
			log.finest("Stream started: $attrs")
			halcyon.eventBus.fire(StreamStartedEvent(attrs))
		}

		override fun onParseError(errorMessage: String) {
			log.finest("Parse error: $errorMessage")
			halcyon.eventBus.fire(ParseErrorEvent(errorMessage))
		}
	}

	private lateinit var ws: WebSocket

	override fun createSessionController(): SessionController = WebSocketSessionController(halcyon, this)

	override fun send(data: CharSequence) {
		if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "Sending: $data")
		try {
			this.ws.send(data.toString())
		} catch (e: Throwable) {
			log.log(Level.WARNING, "Cannot send data.", e)
			halcyon.eventBus.fire(WebSocketConnectionErrorEvent("Cannot send data"))
			throw e
		}
	}

	private fun getDomain(): String {
		val userJid = halcyon.sessionObject.getProperty<BareJID>(SessionObject.USER_BARE_JID)

		return halcyon.sessionObject.getProperty<String>(SessionObject.DOMAIN_NAME) ?: (userJid?.domain
			?: throw HalcyonException("No domain is specified"))
	}

	override fun start() {
		log.log(Level.FINE, "Starting WebSocket connector")
		state = State.Connecting

		val url = "ws://${getDomain()}:5290/"

		log.log(Level.FINER, "Connecting to $url")

		this.ws = WebSocket(url, "xmpp")

		log.log(Level.FINEST, "Created WS: $ws")

		ws.onmessage = this::onSocketMessageEvent
		ws.onerror = this::onSocketError
		ws.onopen = this::onSocketOpen
		ws.onclose = this::onSocketClose

	}

	private fun onSocketClose(event: Event): dynamic {
		log.fine("Socket is closed: $event")
		var oldState = state
		state = State.Disconnected
		if (oldState == State.Connected) halcyon.eventBus.fire(WebSocketConnectionErrorEvent("Socket unexpectedly disconnected."))
		return true
	}

	private fun onSocketOpen(event: Event): dynamic {
		log.fine("Socket opened $event")
		state = State.Connected
		whitespacePingExecutor.start()

		restartStream()

		return true
	}

	private fun onSocketError(event: Event): dynamic {
		log.warning("Socket error: $event")
		state = State.Disconnected

		halcyon.eventBus.fire(WebSocketConnectionErrorEvent("Unknown error"))
		return true
	}

	private fun onSocketMessageEvent(event: MessageEvent): dynamic {
		log.fine("Received: ${event.data}")
		parser.parse(event.data.toString())

		return true
	}

	override fun stop() {
		log.info("Stopping WebSocket connector")
		whitespacePingExecutor.stop()
		if (state == State.Connected) closeStream()
		state = State.Disconnecting
		this.ws.close()
	}

	private fun closeStream() {
		send("</stream:stream>")
	}

	fun restartStream() {
		log.finest("Send new stream")
		val userJid = halcyon.sessionObject.getProperty<BareJID>(SessionObject.USER_BARE_JID)

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
			log.fine("Whitespace ping")
			this.ws.send("")
		}
	}

}