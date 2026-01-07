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
package tigase.halcyon.core.eventbus

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.TickEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

actual class EventBus actual constructor(context: AbstractHalcyon) : AbstractEventBus(context) {

	actual override fun createHandlersMap(): MutableMap<String, MutableSet<EventHandler<*>>> = ConcurrentHashMap()

	actual override fun createHandlersSet(): MutableSet<EventHandler<*>> = ConcurrentHashMap.newKeySet()

	enum class Mode { NoThread,
		ThreadPerEvent,
		ThreadPerHandler
	}

	private var threadCounter = 0

	var mode = Mode.NoThread

	private val executor = Executors.newSingleThreadExecutor { r ->
		val t = Thread(r)
		t.name = "EventBus-Thread-" + ++threadCounter
		t.isDaemon = true
		t
	}

	@Suppress("UNCHECKED_CAST")
	private fun fireNoThread(event: Event, handlers: Collection<EventHandler<*>>) {
		handlers.forEach { eventHandler ->
			try {
				(eventHandler as EventHandler<Event>).onEvent(event)
			} catch (e: Exception) {
				log.warning(e) { "Problem on handling event ${event.eventType}" }
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun fireThreadPerEvent(event: Event, handlers: Collection<EventHandler<*>>) {
		executor.execute {
			handlers.forEach { eventHandler ->
				try {
					(eventHandler as EventHandler<Event>).onEvent(event)
				} catch (e: Exception) {
					log.warning(e) { "Problem on handling event ${event.eventType}" }
				}
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun fireThreadPerHandler(event: Event, handlers: Collection<EventHandler<*>>) {
		handlers.forEach { eventHandler ->
			executor.execute {
				try {
					(eventHandler as EventHandler<Event>).onEvent(event)
				} catch (e: Exception) {
					log.warning(e) { "Problem on handling event ${event.eventType}" }
				}
			}
		}
	}

	override fun fire(event: Event, handlers: Collection<EventHandler<*>>) {
		if(event !is TickEvent)
		log.finest { "Firing event $event with ${handlers.size} handlers" }

		when (mode) {
			Mode.NoThread -> fireNoThread(event, handlers)
			Mode.ThreadPerEvent -> fireThreadPerEvent(event, handlers)
			Mode.ThreadPerHandler -> fireThreadPerHandler(event, handlers)
		}

	}

}
