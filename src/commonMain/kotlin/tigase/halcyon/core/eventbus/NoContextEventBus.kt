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

import tigase.halcyon.core.TickEvent
import tigase.halcyon.core.eventbus.EventBusInterface.Companion.ALL_EVENTS
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory

@Suppress("LeakingThis")
abstract class NoContextEventBus : EventBusInterface {

	protected val log = LoggerFactory.logger("tigase.halcyon.core.eventbus.EventBus")

	protected var handlersMap: MutableMap<String, MutableSet<EventHandler<*>>> = createHandlersMap()

	protected abstract fun createHandlersMap(): MutableMap<String, MutableSet<EventHandler<*>>>

	protected abstract fun createHandlersSet(): MutableSet<EventHandler<*>>

	private fun getHandlers(eventType: String): Collection<EventHandler<*>> {
		val result = HashSet<EventHandler<*>>()

		val a = handlersMap[ALL_EVENTS]
		if (a != null && a.isNotEmpty()) {
			result.addAll(a)
		}

		val h = handlersMap[eventType]
		if (h != null && h.isNotEmpty()) {
			result.addAll(h)
		}

		return result
	}

	abstract fun updateBeforeFire(event: Event)

	override fun fire(event: Event) {
		updateBeforeFire(event)
		val handlers = getHandlers(event.eventType)
		fire(event, handlers)
	}

	@Suppress("UNCHECKED_CAST")
	protected open fun fire(event: Event, handlers: Collection<EventHandler<*>>) {
		if (event !is TickEvent || log.isLoggable(Level.FINEST)) log.fine { "Firing event $event with ${handlers.size} handlers" }
		handlers.forEach { eventHandler ->
			try {
				(eventHandler as EventHandler<Event>).onEvent(event)
			} catch (e: Exception) {
				log.warning(e) { "Problem on handling event" }
			}
		}
	}

	override fun <T : Event> register(eventType: String, handler: EventHandler<T>) {
		var handlers = handlersMap[eventType]
		if (handlers == null) {
			handlers = createHandlersSet()
			handlersMap[eventType] = handlers
		}
		handlers.add(handler)
	}

	override fun <T : Event> register(eventType: String, handler: (T) -> Unit) {
		register(eventType, object : EventHandler<T> {
			override fun onEvent(event: T) {
				handler.invoke(event)
			}
		})
	}

	override fun unregister(eventType: String, handler: EventHandler<*>) {
		handlersMap[eventType]?.remove(handler)
	}

	override fun unregister(handler: EventHandler<*>) {
		for ((_, handlers) in handlersMap) {
			handlers.remove(handler)
		}
	}
}