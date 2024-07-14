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
package tigase.halcyon.core.connector.socket

import kotlinx.cinterop.toKString
import platform.Network.nw_error_get_error_code
import platform.Network.nw_error_t
import platform.darwin.*
import platform.posix.usleep
import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.configuration.declaredUserJID
import tigase.halcyon.core.connector.*
import tigase.halcyon.core.excutor.TickExecutor
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xml.parser.StreamParser
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import kotlin.time.Duration.Companion.seconds

sealed class SocketConnectionErrorEvent : ConnectionErrorEvent() {

	class TLSFailureEvent : SocketConnectionErrorEvent()
	class HostNotFount : SocketConnectionErrorEvent()
	class Timeout : SocketConnectionErrorEvent()
	class Unknown(val caught: nw_error_t?) : SocketConnectionErrorEvent() {

		override fun toString(): String {
			return "tigase.halcyon.core.connector.socket.SocketConnectionErrorEvent.Unknown:NWERROR:" + nw_error_get_error_code(
				caught
			)
		}
	}

}

class SocketConnector(halcyon: Halcyon) : AbstractConnector(halcyon) {

	companion object {

		const val SERVER_HOST = "tigase.halcyon.core.connector.socket.SocketConnector#serverHost"
		const val SERVER_PORT = "tigase.halcyon.core.connector.socket.SocketConnector#serverPort"
		const val SEE_OTHER_HOST_KEY = "tigase.halcyon.core.connector.socket.SocketConnector#seeOtherHost"

		const val XMLNS_START_TLS = "urn:ietf:params:xml:ns:xmpp-tls"
	}

	var secured: Boolean = false
		private set

	private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.SocketConnector")
	private var config: SocketConnectorConfig = halcyon.config.connection as SocketConnectorConfig
	private var resolver: DnsResolver = DnsResolver()
	private val whitespacePingExecutor = TickExecutor(halcyon.eventBus, 30.seconds) { onTick() }
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
			processReceivedElement(element)
		}

		override fun onStreamClosed() {
			log.finest { "Stream closed" }
			if (state != State.Disconnected) {
				halcyon.eventBus.fire(StreamTerminatedEvent())
			}
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

	private var socket: Socket? = null
	private var sslEngine: SSLEngine? = null
	private val queue = dispatch_queue_create("SocketConnector_Network", null)

	override fun createSessionController(): SessionController {
		return SocketSessionController(halcyon, this)
	}

	override fun send(data: CharSequence) {
		val bytes = StringBuilder().append(data)
			.toString()
			.encodeToByteArray()
		sslEngine?.let { sslEngine ->
			sslEngine.encrypt(bytes)
		} ?: run {
			writeDataToSocket(bytes)
		}
	}

	fun restartStream() {
		log.finest("restarting stream..")
		val userJid = halcyon.config.declaredUserJID
		val domain = (halcyon.config.connection as SocketConnectorConfig).domain

		val sb = buildString {
			append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ")
			append("version='1.0' ")
			if (userJid != null) append("from='$userJid' ")
			append("to='${domain}'")
			append(">")
		}
		send(sb)
	}

	fun startTLS() {
		log.info { "Running StartTLS" }
		val element = element("starttls") {
			xmlns = XMLNS_START_TLS
		}
		halcyon.writer.writeDirectly(element)
	}

	override fun start() {
		//this.ensureNeverFrozen();
		state = State.Connecting

		socket = Socket()
		resolveTarget { name, port ->
			socket?.readCallback = { data ->
				log.finest("read data: " + data.size)
				this.processSocketData(data)
			}
			socket?.stateCallback = { state ->
				when (state) {
					Socket.State.connecting -> {}
					Socket.State.connected -> {
						log.fine { "Connection established" }
						this.state = State.Connected
						restartStream()
					}

					Socket.State.disconnected -> {
						this.state = State.Disconnected
					}
				}
			}

			log.fine { "Opening socket connection" }
			socket?.connect(name = name, port = port)
			socket?.startProcessing()
		}
	}

	override fun stop() {
		if ((state != State.Disconnected)) {
			log.fine { "Stopping..." }
			try {
				if (state == State.Connected) closeStream()
				state = State.Disconnecting
				whitespacePingExecutor.stop()
				usleep(175000u)
				if (state != State.Disconnected) {
					socket?.disconnect()
				}
			} finally {
				// delayed firing "disconnected" event, to delay reconnection to process all events before reconnection
				// will start - ending Socket kqueue loop
				dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 10L * NSEC_PER_MSEC.toLong()), queue) {
					state = State.Disconnected
					eventsEnabled = false
				}
			}
		}
	}

	private fun closeStream() {
		send("</stream:stream>")
	}

	private fun connectionError(cause: SocketConnectionErrorEvent) {
		halcyon.eventBus.fire(cause)
		state = when (state) {
			State.Connecting -> State.Disconnected
			State.Connected -> State.Disconnecting
			State.Disconnecting -> State.Disconnected
			State.Disconnected -> State.Disconnected
		}
		if (state == State.Disconnected) eventsEnabled = false
	}

	private fun processSocketData(data: ByteArray) {
		sslEngine?.let { sslEngine ->
			sslEngine.decrypt(data)
		} ?: run {
			process(data)
		}
	}

	private fun processReceivedElement(element: Element) {
		when (element.xmlns) {
			// FIXME: NOT IMPLEMENTED YET!!
			XMLNS_START_TLS -> processTLSStanza(element)
			else -> halcyon.eventBus.fire(ReceivedXMLElementEvent(element))
		}
	}

	private fun processTLSStanza(element: Element) {
		when (element.name) {
			"proceed" -> {
				proceedTLS()
			}

			"failure" -> {
				log.warning { "Cannot establish TLS connection!" }
				halcyon.eventBus.fire(SocketConnectionErrorEvent.TLSFailureEvent())
			}

			else -> throw XMPPException(ErrorCondition.BadRequest)
		}
	}

	private fun proceedTLS() {
		log.info { "Proceeding TLS" }
		val domain = (halcyon.config.connection as SocketConnectorConfig).domain
		sslEngine = SSLEngine(this, domain)
		restartStream()
	}

	private fun resolveTarget(completionHandler: (String, Int) -> Unit) {
		val location = halcyon.getModuleOrNull(StreamManagementModule)?.resumptionContext?.location
		if (location != null) {
			return completionHandler(location, config.port)
		}

		val seeOther = halcyon.internalDataStore.getData<String>(SEE_OTHER_HOST_KEY)
		if (seeOther != null) {
			return completionHandler(seeOther, config.port)
		}

		(halcyon.config.connection as SocketConnectorConfig).hostname?.let {
			return completionHandler(it, config.port)
		}

//		val forcedHost = halcyon.sessionObject.getProperty<String>(SERVER_HOST)
//		if (forcedHost != null) {
//			return Socket(InetAddress.getByName(forcedHost), config.port)
//		}
		val domain = (halcyon.config.connection as SocketConnectorConfig).domain
		resolver = DnsResolver()
		resolver.resolve(domain) { result ->
			result.onSuccess { records ->
				completionHandler(records.first().target, records.first().port.toInt())
			}
				.onFailure {
					completionHandler(domain, config.port)
				}
		}
	}

	private fun onTick() {
		// FIXME: Do we need whitespace ping??
//        if (state == State.Connected && whiteSpaceEnabled) {
//            log.finer { "Whitespace ping" }
//            log.finer { "Whitespace ping" }
//            worker.writer.write(' '.code)
//            worker.writer.flush()
//        }
	}

	@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
	fun process(data: ByteArray) {
		log.finest {
			"Received " + data.toKString()
				.let {
					if (it.length <= 200) it else (it.subSequence(0, 200)
						.toString() + "...")
				}
		}
		parser.parse(data.toKString())
	}

	fun writeDataToSocket(data: ByteArray) {
		socket?.send(data)
	}
}