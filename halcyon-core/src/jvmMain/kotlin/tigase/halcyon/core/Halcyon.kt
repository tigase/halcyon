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
package tigase.halcyon.core

import tigase.halcyon.core.connector.socket.SocketConnector
import java.util.*

actual class Halcyon actual constructor() : tigase.halcyon.core.AbstractHalcyon() {

	override fun createConnector(): tigase.halcyon.core.connector.AbstractConnector {
		return SocketConnector(this)
	}

	override fun reconnect(immediately: Boolean) {
		if (!immediately) Thread.sleep(3000)
		state = State.Connecting
		startConnector()
	}

	val timer = Timer("timer", true)

	private val lock = java.lang.Object()

	private lateinit var tickTask: TimerTask

	init {
		eventBus.mode = tigase.halcyon.core.eventbus.EventBus.Mode.ThreadPerHandler
	}

	override fun onConnecting() {
		super.onConnecting()
		tickTask = object : TimerTask() {
			override fun run() {
				tick()
			}
		}
		timer.scheduleAtFixedRate(tickTask, 2_000, 2_000)
	}

	override fun onDisconnecting() {
		tickTask.cancel()
		super.onDisconnecting()
	}

//	fun connect(sync: Boolean) {
//		super.connect()
//		synchronized(lock) {
//			lock.wait(30000)
//		}
//	}
}