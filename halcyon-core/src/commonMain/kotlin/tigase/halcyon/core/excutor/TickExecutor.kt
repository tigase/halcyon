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
package tigase.halcyon.core.excutor

import tigase.halcyon.core.TickEvent
import tigase.halcyon.core.eventbus.AbstractEventBus
import tigase.halcyon.core.eventbus.EventHandler

class TickExecutor(
	private val eventBus: AbstractEventBus, val minimalTime: Long, private val runnable: () -> Unit
) {

	private val handler: EventHandler<TickEvent> = object : EventHandler<TickEvent> {
		override fun onEvent(event: TickEvent) {
			onTick(event)
		}
	}

	init {
		start()
	}

	private var lastCallTime = -1L

	private fun onTick(event: TickEvent) {
		if (lastCallTime + minimalTime <= event.timestamp) {
			lastCallTime = event.timestamp
			runnable.invoke()
		}
	}

	fun start() {
		eventBus.register(TickEvent.TYPE, handler)
	}

	fun stop() {
		eventBus.unregister(TickEvent.TYPE, handler)
	}

}