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

import org.minidns.dnssec.DnssecValidationFailedException
import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.configuration.declaredUserJID
import tigase.halcyon.core.connector.*
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.excutor.TickExecutor
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xml.parser.StreamParser
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.seconds

sealed class SocketConnectionErrorEvent : ConnectionErrorEvent() {

	class TLSFailureEvent : SocketConnectionErrorEvent()
	class HostNotFount : SocketConnectionErrorEvent()
	class Unknown(val caught: Throwable) : SocketConnectionErrorEvent() {

		override fun toString(): String {
			caught.printStackTrace()
			return "tigase.halcyon.core.connector.socket.SocketConnectionErrorEvent.Unknown: " + caught.message
		}
	}

}

class HostNotFound : HalcyonException()

typealias HostPort = Pair<String, Int>

class SocketConnector(halcyon: Halcyon, val tlsProcesorFactory: TLSProcessorFactory) : AbstractConnector(halcyon),
	ChannelBindingDataProvider {

	companion object {

		const val SEE_OTHER_HOST_KEY = "tigase.halcyon.core.connector.socket.SocketConnector#seeOtherHost"

		const val XMLNS_START_TLS = "urn:ietf:params:xml:ns:xmpp-tls"
	}

	private lateinit var tlsProcesor: TLSProcessor

	val secured: Boolean
		get() = tlsProcesor.isConnectionSecure()

	private var started = false

	private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.SocketConnector")

	private lateinit var socket: Socket

	private lateinit var worker: SocketWorker

	private val whitespacePingExecutor = TickExecutor(halcyon.eventBus, 30.seconds) { onTick() }

	private var whiteSpaceEnabled: Boolean = true

	private var config: SocketConnectorConfig = halcyon.config.connection as SocketConnectorConfig

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
			this@SocketConnector.fire(StreamTerminatedEvent())
		}

		override fun onStreamStarted(attrs: Map<String, String>) {
			log.finest { "Stream started: $attrs" }
			this@SocketConnector.fire(StreamStartedEvent(attrs))
		}

		override fun onParseError(errorMessage: String) {
			log.finest { "Parse error: $errorMessage" }
			this@SocketConnector.fire(ParseErrorEvent(errorMessage))
		}
	}

	private fun processReceivedElement(element: Element) {
		when (element.xmlns) {
			XMLNS_START_TLS -> processTLSStanza(element)
			else -> this@SocketConnector.fire(ReceivedXMLElementEvent(element))
		}
	}

	private fun processTLSStanza(element: Element) {
		when (element.name) {
			"proceed" -> {
				proceedTLS()
			}

			"failure" -> {
				log.warning { "Cannot establish TLS connection!" }
				fire(SocketConnectionErrorEvent.TLSFailureEvent())
			}

			else -> throw XMPPException(ErrorCondition.BadRequest)
		}
	}


	private fun proceedTLS() {
		log.info { "Proceeding TLS" }
		try {
			log.finest { "Disabling whitespace ping" }
			whiteSpaceEnabled = false

			tlsProcesor.proceedTLS { inputStream, outputStream ->
				worker.setReaderAndWriter(
					InputStreamReader(inputStream), OutputStreamWriter(outputStream)
				)
			}

			restartStream()
		} catch (e: Throwable) {
			state = State.Disconnecting
			fire(createSocketConnectionErrorEvent(e))
		} finally {
			log.finest { "Enabling whitespace ping" }
			whiteSpaceEnabled = true
		}
	}

	override fun createSessionController(): SessionController = SocketSessionController(halcyon, this)

	private fun resolveTarget(completionHandler: (List<HostPort>) -> Unit) {
		val hosts = mutableListOf<HostPort>()

		val location = halcyon.getModuleOrNull(StreamManagementModule)?.resumptionContext?.location
		if (location != null) {
			hosts += HostPort(location, config.port)
			log.fine { "Using host ${location}:${config.port}" }
			completionHandler(hosts)
			return
		}

		val seeOther = halcyon.internalDataStore.getData<String>(SEE_OTHER_HOST_KEY)
		if (seeOther != null) {
			hosts += HostPort(seeOther, config.port)
			log.fine { "Using host ${seeOther}:${config.port}" }
			completionHandler(hosts)
			return
		}

		if (config.hostname != null) {
			hosts += HostPort(config.hostname!!, config.port)
			log.fine { "Using host ${config.hostname}:${config.port}" }
			completionHandler(hosts)
			return
		}

		log.fine { "Resolving DNS of ${config.domain}" }
		config.dnsResolver.resolve(config.domain) { result ->
			result.onFailure {
				hosts += HostPort(config.domain, config.port)
			}
			result.onSuccess {
				hosts.addAll(it.shuffled().map { HostPort(it.target, it.port.toInt()) })
			}

			completionHandler(hosts)
		}
	}

	private fun createSocket(completionHandler: (Socket) -> Unit) {
		resolveTarget { hosts ->
			hosts.forEach { hp ->
				try {
					log.fine { "Opening connection to ${hp.first}:${hp.second}" }
					val s = Socket(hp.first, hp.second)
					completionHandler(s)
					return@resolveTarget
				} catch (e: Throwable) {
					log.fine { "Host ${hp.first}:${hp.second} is unreachable." }
				}
			}
			throw HostNotFound()
		}
	}

	override fun start() {
		started = true
		state = State.Connecting

		val userJid = halcyon.config.declaredUserJID
		val domain = (halcyon.config.connection as SocketConnectorConfig).domain
		try {
			createSocket { sckt ->
				this.socket = sckt
				socket.soTimeout = 20 * 1000
				socket.keepAlive = false
				socket.tcpNoDelay = true
				log.fine { "Opening socket connection to ${this.socket.inetAddress}" }
				this.worker = SocketWorker(parser).apply {
					setReaderAndWriter(
						InputStreamReader(socket.getInputStream()), OutputStreamWriter(socket.getOutputStream())
					)
				}
				this.worker.onError = { exception -> onWorkerException(exception) }
				this.tlsProcesor = tlsProcesorFactory.create(this.socket, config)
				worker.start()
				val sb = buildString {
					append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ")
					append("version='1.0' ")
					if (userJid != null) append("from='$userJid' ")
					append("to='${domain}'")
					append(">")

				}
				send(sb)

				state = State.Connected
				whitespacePingExecutor.start()
			}
		} catch (e: HostNotFound) {
			state = State.Disconnected
			fire(SocketConnectionErrorEvent.HostNotFount())
		} catch (e: Exception) {
			state = State.Disconnected
			fire(createSocketConnectionErrorEvent(e))
			eventsEnabled = false
		}
	}

	private fun onWorkerException(cause: Exception) {
		cause.printStackTrace()
		fire(createSocketConnectionErrorEvent(cause))
		state = when (state) {
			State.Connecting -> State.Disconnected
			State.Connected -> State.Disconnecting
			State.Disconnecting -> State.Disconnected
			State.Disconnected -> State.Disconnected
		}
		if (state == State.Disconnected) eventsEnabled = false
	}

	private fun createSocketConnectionErrorEvent(cause: Throwable): SocketConnectionErrorEvent = when (cause) {
		is UnknownHostException, is DnssecValidationFailedException -> SocketConnectionErrorEvent.HostNotFount()
		else -> SocketConnectionErrorEvent.Unknown(cause)
	}

	override fun stop() {
		started = false
		if ((state != State.Disconnected)) {
			log.fine { "Stopping..." }
			try {
				if (state == State.Connected) closeStream()
				state = State.Disconnecting
				whitespacePingExecutor.stop()
				Thread.sleep(175)
				if (!this.socket.isClosed) {
					worker.writer.close()
					this.socket.close()
				}
				worker.interrupt()
				tlsProcesor.clear()
				while (worker.isActive) Thread.sleep(32)
			} finally {
				log.fine { "Stopped" }
				state = State.Disconnected
				eventsEnabled = false
			}
		}
	}

	private fun closeStream() {
		if (state == State.Connected) send("</stream:stream>")
	}

	override fun send(data: CharSequence) {
		try {
			log.finest { "Sending (${socket.isConnected}, ${!socket.isOutputShutdown}): $data" }
			worker.writer.write(data.toString())
			worker.writer.flush()
		} catch (e: Exception) {
			log.warning(e) { "Cannot send data to server" }
			state = State.Disconnecting
			fire(createSocketConnectionErrorEvent(e))
			throw e
		}
	}

	fun restartStream() {
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

	private fun onTick() {
		if (state == State.Connected && whiteSpaceEnabled) {
			log.finer { "Whitespace ping" }
			worker.writer.write(' '.code)
			worker.writer.flush()
		}
	}

	fun startTLS() {
		log.info { "Running StartTLS" }
		whiteSpaceEnabled = false
		val element = element("starttls") {
			xmlns = XMLNS_START_TLS
		}
		halcyon.writer.writeDirectly(element)
	}

	override fun isConnectionSecure(): Boolean = tlsProcesor.isConnectionSecure()

	override fun getTlsUnique(): ByteArray? = tlsProcesor.getTlsUnique()

	override fun getTlsServerEndpoint(): ByteArray? = tlsProcesor.getTlsServerEndpoint()

	override fun getTlsExporter(): ByteArray? = tlsProcesor.getTlsServerEndpoint()

}