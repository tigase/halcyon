package org.tigase.jaxmpp.core.connector.socket

import org.minidns.hla.DnssecResolverApi
import org.minidns.hla.SrvType
import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.SessionObject
import org.tigase.jaxmpp.core.connector.*
import org.tigase.jaxmpp.core.exceptions.JaXMPPException
import org.tigase.jaxmpp.core.logger.Level
import org.tigase.jaxmpp.core.logger.Logger
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xml.parser.StreamParser
import org.tigase.jaxmpp.core.xmpp.BareJID
import org.tigase.jaxmpp.core.xmpp.SessionController
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.Socket

class SocketConnector(context: Context) : AbstractConnector(context) {

	companion object {
		const val SERVER_HOST = "SocketConnector.serverHost"
		const val SERVER_PORT = "SocketConnector.serverPort"
	}

	private val log = Logger("org.tigase.jaxmpp.core.connector.socket.SocketConnector")

	private lateinit var socket: Socket

	private val parser = object : StreamParser() {
		override fun onNextElement(element: Element) {
			log.finest("Received element ${element.getAsString()}")
			context.eventBus.fire(ReceivedXMLElementEvent(element))
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
		}
	}

	override fun createSessionController(): SessionController = SocketSessionController(context, this)

	internal lateinit var worker: SocketWorker

	protected fun createSocket(): Socket {
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
		throw JaXMPPException("Cannot open socket")
	}

	override fun start() {
		state = State.Connecting

		val userJid = context.sessionObject.getProperty<BareJID>(SessionObject.USER_BARE_JID)!!



		this.socket = createSocket()
		log.fine("Opening socket connection to ${this.socket.inetAddress}")

		val writer = OutputStreamWriter(this.socket.getOutputStream())
		this.worker = SocketWorker(socket, parser, writer)
		worker.start()

		val sb = StringBuilder()
		sb.append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ");
		sb.append("version='1.0' ")
		sb.append("from='$userJid' ")
		sb.append("to='${userJid.domain}'>")

		send(sb)

		state = State.Connected

	}

	override fun stop() {
		if (state == State.Connecting || state == State.Connected) {
			state = State.Disconnecting
			if (!this.socket.isClosed) this.socket.close()
			worker.interrupt()
			while (worker.isActive) Thread.sleep(32)
			state = State.Disconnected
		}
	}

	override fun send(data: CharSequence) {
		if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "Sending: $data")
		worker.writer.write(data.toString())
		worker.writer.flush()
	}

	fun restartStream() {
		val userJid = context.sessionObject.getProperty<BareJID>(SessionObject.USER_BARE_JID)!!

		val sb = StringBuilder()
		sb.append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ");
		sb.append("version='1.0' ")
		sb.append("from='$userJid' ")
		sb.append("to='${userJid.domain}'>")

		send(sb)
	}

}