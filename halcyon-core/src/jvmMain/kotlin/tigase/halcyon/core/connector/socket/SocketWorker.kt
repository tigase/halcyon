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

import tigase.halcyon.core.connector.ConnectorException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xml.parser.StreamParser
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.net.Socket

class SocketWorker(s: Socket, private val parser: StreamParser) : Thread() {

	private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.SocketWorker")

	var onActiveChange: ((Boolean) -> Unit)? = null

	var isActive = false
		private set(value) {
			val tmp = field
			field = value
			if (tmp != field) onActiveChange?.invoke(field)
		}

	var socket: Socket = s
		set(value) {
			field = value
			update()
		}

	private lateinit var reader: Reader
	lateinit var writer: OutputStreamWriter
		private set
	var onError: ((Exception) -> Unit)? = null

	init {
		name = "Socket-Worker-Thread"
		isDaemon = true
		update()
	}

	private fun update() {
		reader = InputStreamReader(socket.getInputStream())
		writer = OutputStreamWriter(this.socket.getOutputStream())
	}

	override fun run() {
		log.fine { "Socket Worker Started" }
		val buffer = CharArray(10240)
		try {
			isActive = true
			while (isActive && !interrupted() && isAlive) {
				val len = reader.read(buffer)
				if (len == -1) {
					log.finest { "Nothing more to read" }
					break
				}

				if (isActive && !interrupted() && isAlive) parser.parse(buffer, 0, len - 1)
			}
			if (isActive) {
				log.warning { "Unexpected stop!" }
				onError?.invoke(ConnectorException("Unexpected stop!"))
			}
		} catch (e: Exception) {
			log.fine { "Exception in worker: isActive=$isActive interrupted=${!interrupted()} socket=${!socket.isClosed}" }
			if (isActive) {
				log.fine(e) { "Exception in Socket Worker" }
				onError?.invoke(e)
			}
		} finally {
			isActive = false
			log.fine { "Socket Worker Stopped" }
		}
	}

	override fun interrupt() {
		isActive = false
		super.interrupt()
	}

}