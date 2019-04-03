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

import org.minidns.hla.DnssecResolverApi
import org.minidns.hla.SrvType
import tigase.halcyon.core.excutor.TickExecutor
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.parser.StreamParser
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.SessionController
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.Socket

class SocketConnector(context: tigase.halcyon.core.Context) : tigase.halcyon.core.connector.AbstractConnector(context) {

	companion object {
		const val SERVER_HOST = "SocketConnector.serverHost"
		const val SERVER_PORT = "SocketConnector.serverPort"
	}

	private val log = tigase.halcyon.core.logger.Logger("tigase.halcyon.core.connector.socket.SocketConnector")

	private lateinit var socket: Socket

	private val whitespacePingExecutor = TickExecutor(context.eventBus, 30000) { onTick() }

	private val parser = object : StreamParser() {
		override fun onNextElement(element: Element) {
			log.finest("Received element ${element.getAsString()}")
			context.eventBus.fire(tigase.halcyon.core.connector.ReceivedXMLElementEvent(element))
		}

		override fun onStreamClosed() {
			log.finest("Stream closed")
			context.eventBus.fire(tigase.halcyon.core.connector.StreamTerminatedEvent())
		}

		override fun onStreamStarted(attrs: Map<String, String>) {
			log.finest("Stream started: $attrs")
			context.eventBus.fire(tigase.halcyon.core.connector.StreamStartedEvent(attrs))
		}

		override fun onParseError(errorMessage: String) {
			log.finest("Parse error: $errorMessage")
			context.eventBus.fire(tigase.halcyon.core.connector.ParseErrorEvent(errorMessage))
		}
	}

	override fun createSessionController(): SessionController = SocketSessionController(context, this)

	internal lateinit var worker: SocketWorker

	protected fun createSocket(): Socket {
		val forcedHost = context.sessionObject.getProperty<String>(SERVER_HOST)

		if (forcedHost != null) {
			return Socket(InetAddress.getByName(forcedHost), 5222)
		}

		val userJid = context.sessionObject.getProperty<BareJID>(tigase.halcyon.core.SessionObject.USER_BARE_JID)!!

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
		throw tigase.halcyon.core.connector.ConnectorException("Cannot open socket")
	}

	override fun start() {
		state = tigase.halcyon.core.connector.State.Connecting

		val userJid = context.sessionObject.getProperty<BareJID>(tigase.halcyon.core.SessionObject.USER_BARE_JID)!!



		this.socket = createSocket()
		log.fine("Opening socket connection to ${this.socket.inetAddress}")

		val writer = OutputStreamWriter(this.socket.getOutputStream())
		this.worker = SocketWorker(socket, parser, writer)
		this.worker.onError = { exception -> onWorkerException(exception) }
		worker.start()

		val sb = StringBuilder()
		sb.append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ")
		sb.append("version='1.0' ")
		sb.append("from='$userJid' ")
		sb.append("to='${userJid.domain}'>")

		send(sb)

		state = tigase.halcyon.core.connector.State.Connected
		whitespacePingExecutor.start()
	}

	private fun onWorkerException(cause: Exception) {
		cause.printStackTrace()
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun stop() {
		if (state == tigase.halcyon.core.connector.State.Connecting || state == tigase.halcyon.core.connector.State.Connected) {
			whitespacePingExecutor.stop()
			state = tigase.halcyon.core.connector.State.Disconnecting
			if (!this.socket.isClosed) this.socket.close()
			worker.interrupt()
			while (worker.isActive) Thread.sleep(32)
			state = tigase.halcyon.core.connector.State.Disconnected
		}
	}

	override fun send(data: CharSequence) {
		if (log.isLoggable(tigase.halcyon.core.logger.Level.FINEST)) log.log(
			tigase.halcyon.core.logger.Level.FINEST,
			"Sending (${worker.socket.isConnected}, ${!worker.socket.isOutputShutdown}): $data"
		)
		worker.writer.write(data.toString())
		worker.writer.flush()
	}

	fun restartStream() {
		val userJid = context.sessionObject.getProperty<BareJID>(tigase.halcyon.core.SessionObject.USER_BARE_JID)!!

		val sb = StringBuilder()
		sb.append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ")
		sb.append("version='1.0' ")
		sb.append("from='$userJid' ")
		sb.append("to='${userJid.domain}'>")

		send(sb)
	}

	private fun onTick() {
		log.fine("Whitespace ping")
		worker.writer.write(' '.toInt())
		worker.writer.flush()
	}

}