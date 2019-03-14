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

	val timer = Timer("timer", true)

	private val tickTask = object : TimerTask() {
		override fun run() {
			tick()
		}
	}

	init {
		eventBus.mode = tigase.halcyon.core.eventbus.EventBus.Mode.ThreadPerHandler
		timer.scheduleAtFixedRate(tickTask, 30_000, 30_000)
	}

}