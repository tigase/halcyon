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
package tigase.halcyon.core.excutor

import kotlinx.datetime.Instant
import tigase.halcyon.core.TickEvent
import tigase.halcyon.core.eventbus.AbstractEventBus
import tigase.halcyon.core.eventbus.EventHandler
import kotlin.time.Duration

class TickExecutor(
	private val eventBus: AbstractEventBus, val minimalTime: Duration, private val runnable: () -> Unit
) {

	private val handler: EventHandler<TickEvent> = object : EventHandler<TickEvent> {
		override fun onEvent(event: TickEvent) {
			onTick(event)
		}
	}

	init {
		start()
	}

	private var lastCallTime = Instant.DISTANT_PAST

	private fun onTick(event: TickEvent) {
		if (lastCallTime + minimalTime <= event.eventTime) {
			lastCallTime = event.eventTime
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