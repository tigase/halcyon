package org.tigase.jaxmpp.core.connector.socket

import org.tigase.jaxmpp.core.logger.Level
import org.tigase.jaxmpp.core.logger.Logger
import org.tigase.jaxmpp.core.xml.parser.StreamParser
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.net.Socket
import java.net.SocketException

class SocketWorker(s: Socket, val parser: StreamParser, val writer: OutputStreamWriter) : Thread() {

	private val log = Logger("org.tigase.jaxmpp.core.connector.socket.SocketWorker")

	var isActive = false
		private set

	var socket: Socket = s
		set(value) {
			field = value
			update()
		}

	private lateinit var reader: Reader

	init {
		name = "Socket-Worker-Thread"
		isDaemon = true
		update()
	}

	private fun update() {
		reader = InputStreamReader(socket.getInputStream())
	}

	override fun run() {
		log.fine("Socket Worker Started")
		val buffer = CharArray(10240)
		try {
			isActive = true
			while (isActive && !interrupted() && isAlive) {
				val len = reader.read(buffer)
				if (len == -1) {
					log.finest("Nothing more to read")
					break
				}

				parser.parse(buffer, 0, len - 1)
			}
		} catch (e: SocketException) {
			log.log(Level.FINE, "Exception in Socket Worker", e)
			if (!socket.isClosed) throw e
		} finally {
			isActive = false
		}
		log.fine("Socket Worker Stopped")
	}

}