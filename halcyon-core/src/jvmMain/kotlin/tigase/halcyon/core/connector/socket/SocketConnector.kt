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
package tigase.halcyon.core.connector.socket

import org.minidns.dnssec.DnssecValidationFailedException
import org.minidns.hla.DnssecResolverApi
import org.minidns.hla.SrvType
import tigase.halcyon.core.Context
import tigase.halcyon.core.SessionObject
import tigase.halcyon.core.connector.*
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventHandler
import tigase.halcyon.core.excutor.TickExecutor
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xml.parser.StreamParser
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.SessionController
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

sealed class SocketConnectionErrorEvent : ConnectionErrorEvent() {

	class TLSFailureEvent : SocketConnectionErrorEvent()
	class HostNotFount : SocketConnectionErrorEvent()
	class Unknown(val caught: Throwable) : SocketConnectionErrorEvent()

}

class SocketConnector(context: Context) : AbstractConnector(context) {

	companion object {
		const val SERVER_HOST = "tigase.halcyon.core.connector.socket.SocketConnector#serverHost"
		const val SERVER_PORT = "tigase.halcyon.core.connector.socket.SocketConnector#serverPort"
		const val SEE_OTHER_HOST_KEY = "tigase.halcyon.core.connector.socket.SocketConnector#seeOtherHost"

		const val XMLNS_START_TLS = "urn:ietf:params:xml:ns:xmpp-tls"
	}

	var secured: Boolean = false
		private set

	private val log = Logger("tigase.halcyon.core.connector.socket.SocketConnector")

	private lateinit var socket: Socket

	private lateinit var worker: SocketWorker

	private val whitespacePingExecutor = TickExecutor(context.eventBus, 30000) { onTick() }

	private var whiteSpaceEnabled: Boolean = true

	private val parser = object : StreamParser() {
		override fun onNextElement(element: Element) {
			processReceivedElement(element)
		}

		override fun onStreamClosed() {
			log.finest("Stream closed")
			context.eventBus.fire(StreamTerminatedEvent())
		}

		override fun onStreamStarted(attrs: Map<String, String>) {
			log.finest("Stream started: $attrs")
			context.eventBus.fire(StreamStartedEvent(attrs))
		}

		override fun onParseError(errorMessage: String) {
			log.finest("Parse error: $errorMessage")
			context.eventBus.fire(ParseErrorEvent(errorMessage))
		}
	}

	private fun processReceivedElement(element: Element) {
		log.finest("Received element ${element.getAsString()}")
		when (element.xmlns) {
			XMLNS_START_TLS -> processTLSStanza(element)
			else -> context.eventBus.fire(ReceivedXMLElementEvent(element))
		}
	}

	private fun processTLSStanza(element: Element) {
		when (element.name) {
			"proceed" -> {
				proceedTLS()
			}
			"failure" -> {
				log.warning("Cannot establish TLS connection!")
				context.eventBus.fire(SocketConnectionErrorEvent.TLSFailureEvent())
			}
			else -> throw XMPPException(ErrorCondition.BadRequest)
		}
	}

	private fun getSocketFactory(): SSLSocketFactory {
		val ctx = SSLContext.getInstance("TLS")

		val trustManagers = arrayOf(object : X509TrustManager {
			override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
				log.finest("Trusted!")
			}

			override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
				log.finest("Trusted!")
			}

			override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
		})
		ctx.init(emptyArray(), trustManagers, SecureRandom())

		return ctx.socketFactory
	}

	private fun proceedTLS() {
		log.info("Proceeding TLS")
		try {
			val userJid = context.sessionObject.getProperty<BareJID>(SessionObject.USER_BARE_JID)!!
			log.finest("Disabling whitespace ping")
			whiteSpaceEnabled = false

			val factory = getSocketFactory()

			val s1 = factory.createSocket(socket, userJid.domain, socket.port, true) as SSLSocket
			s1.soTimeout = 0
			s1.keepAlive = false
			s1.tcpNoDelay = true
			s1.useClientMode = true
			s1.addHandshakeCompletedListener { handshakeCompletedEvent ->
				log.info("Handshake completed $handshakeCompletedEvent")
				secured = true
			}

			s1.startHandshake()


			worker.socket = s1
			restartStream()
		} catch (e: Throwable) {
			state = State.Disconnecting
			context.eventBus.fire(createSocketConnectionErrorEvent(e))
		} finally {
			log.finest("Enabling whitespace ping")
			whiteSpaceEnabled = true
		}
	}

	override fun createSessionController(): SessionController = SocketSessionController(context, this)

	private fun createSocket(): Socket {
		val location = StreamManagementModule.getLocationAddress(context.sessionObject)
		if (location != null) {
			return Socket(InetAddress.getByName(location), 5222)
		}

		val seeOther = context.sessionObject.getProperty<String>(SEE_OTHER_HOST_KEY)
		if (seeOther != null) {
			return Socket(InetAddress.getByName(seeOther), 5222)
		}

		val forcedHost = context.sessionObject.getProperty<String>(SERVER_HOST)
		if (forcedHost != null) {
			return Socket(InetAddress.getByName(forcedHost), 5222)
		}

		val userJid = context.sessionObject.getProperty<BareJID>(SessionObject.USER_BARE_JID)!!

		val result = DnssecResolverApi.INSTANCE.resolveSrv(SrvType.xmpp_client, userJid.domain)

		if (!result.wasSuccessful() || result.answers.isEmpty()) {
			return Socket(InetAddress.getByName(userJid.domain), 5222)
		}

		val srvRecords = result.answers
		srvRecords.forEach {
			try {
				val port = it.port
				val name = it.target.toString()
				return Socket(InetAddress.getByName(name), port)
			} catch (e: Exception) {
				e.printStackTrace()
			}

		}
		throw ConnectorException("Cannot open socket")
	}

	override fun start() {
		state = State.Connecting

		val userJid = context.sessionObject.getProperty<BareJID>(SessionObject.USER_BARE_JID)!!

		try {
			this.socket = createSocket()
			socket.soTimeout = 0
			socket.keepAlive = false
			socket.tcpNoDelay = true
			log.fine("Opening socket connection to ${this.socket.inetAddress}")

			this.worker = SocketWorker(socket, parser)
			this.worker.onError = { exception -> onWorkerException(exception) }
			worker.start()

			val sb = buildString {
				append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ")
				append("version='1.0' ")
				append("from='$userJid' ")
				append("to='${userJid.domain}'>")
			}
			send(sb)

			state = State.Connected
			whitespacePingExecutor.start()
		} catch (e: Exception) {
			state = State.Disconnected
			context.eventBus.fire(createSocketConnectionErrorEvent(e))
		}
	}

	private fun onWorkerException(cause: Exception) {
		cause.printStackTrace()
		state = State.Disconnecting
		context.eventBus.fire(createSocketConnectionErrorEvent(cause))
	}

	private fun createSocketConnectionErrorEvent(cause: Throwable): SocketConnectionErrorEvent =
		when (cause) {
			is UnknownHostException, is DnssecValidationFailedException -> SocketConnectionErrorEvent.HostNotFount()
			else -> SocketConnectionErrorEvent.Unknown(cause)
		}

	override fun stop() {
		if ((state != State.Disconnected)) {
			log.fine("Stopping...")
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


				while (worker.isActive) Thread.sleep(32)
			} finally {
				state = State.Disconnected
			}
		}
	}

	private fun closeStream() {
		if (state == State.Connected) send("</stream:stream>")
	}

	override fun send(data: CharSequence) {
		try {
			if (log.isLoggable(Level.FINEST)) log.log(
				Level.FINEST,
				"Sending (${worker.socket.isConnected}, ${!worker.socket.isOutputShutdown}): $data"
			)
			worker.writer.write(data.toString())
			worker.writer.flush()
		} catch (e: Exception) {
			log.log(Level.WARNING, "Cannot send data to server", e)
			state = State.Disconnecting
			context.eventBus.fire(createSocketConnectionErrorEvent(e))
			throw e
		}
	}

	fun restartStream() {
		val userJid = context.sessionObject.getProperty<BareJID>(SessionObject.USER_BARE_JID)!!

		val sb = buildString {
			append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ")
			append("version='1.0' ")
			append("from='$userJid' ")
			append("to='${userJid.domain}'>")
		}
		send(sb)
	}

	private fun onTick() {
		if (state == State.Connected && whiteSpaceEnabled) {
			log.fine("Whitespace ping")
			worker.writer.write(' '.toInt())
			worker.writer.flush()
		}
	}

	fun startTLS() {
		log.info("Running StartTLS")
		whiteSpaceEnabled = false
		val element = element("starttls") {
			xmlns = XMLNS_START_TLS
		}
		context.writer.writeDirectly(element)
	}

}