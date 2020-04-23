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
package tigase.halcyon.core.eventbus

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.logger.Level

abstract class AbstractEventBus(val context: AbstractHalcyon) {

	companion object {
		const val ALL_EVENTS = "EventBus#ALL_EVENTS"
	}

	protected val log = tigase.halcyon.core.logger.Logger("tigase.halcyon.core.eventbus.EventBus")

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

	fun fire(event: Event) {
		event.eventTime = currentTimestamp()
		event.context = context
		val handlers = getHandlers(event.eventType)
		fire(event, handlers)
	}

	@Suppress("UNCHECKED_CAST")
	protected open fun fire(event: Event, handlers: Collection<EventHandler<*>>) {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Firing event $event with ${handlers.size} handlers")
		}
		handlers.forEach { eventHandler ->
			try {
				(eventHandler as EventHandler<Event>).onEvent(event)
			} catch (e: Exception) {
				if (log.isLoggable(Level.WARNING)) log.log(
					Level.WARNING, "Problem on handling event", e
				)
			}
		}
	}

	fun <T : Event> register(eventType: String = ALL_EVENTS, handler: EventHandler<T>) {
		var handlers = handlersMap[eventType]
		if (handlers == null) {
			handlers = createHandlersSet()
			handlersMap[eventType] = handlers
		}
		handlers.add(handler)
	}

	fun <T : Event> register(eventType: String = ALL_EVENTS, handler: (T) -> Unit) {
		register(eventType, object : EventHandler<T> {
			override fun onEvent(event: T) {
				handler.invoke(event)
			}
		})
	}

	fun unregister(
		eventType: String = ALL_EVENTS, handler: EventHandler<*>
	) {
		val handlers = handlersMap[eventType]
		if (handlers != null) {
			handlers.remove(handler)
//				if (handlers.isEmpty()) {
//					handlersMap.remove(eventType)
//				}
		}
	}

	fun unregister(handler: EventHandler<*>) {
		for ((_, handlers) in handlersMap) {
			handlers.remove(handler)
		}
	}

}