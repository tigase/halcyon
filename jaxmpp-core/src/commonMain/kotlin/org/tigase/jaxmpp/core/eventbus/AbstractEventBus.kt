package org.tigase.jaxmpp.core.eventbus

import org.tigase.jaxmpp.core.SessionObject
import org.tigase.jaxmpp.core.logger.Level
import org.tigase.jaxmpp.core.logger.Logger

open class AbstractEventBus(val sessionObject: SessionObject) {

	companion object {
		const val ALL_EVENTS = "EventBus#ALL_EVENTS"
	}

	protected val log = Logger("org.tigase.jaxmpp.core.eventbus.EventBus")

	protected var handlersMap = HashMap<String, MutableCollection<EventHandler<*>>>()// createHandlersMap()

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
		val handlers = synchronized(this) {
			getHandlers(event.type)
		}
		fire(event, handlers)
	}

	protected open fun fire(event: Event, handlers: Collection<EventHandler<*>>) {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Firing event $event with ${handlers.size} handlers")
		}
		handlers.forEach { eventHandler ->
			try {
				(eventHandler as EventHandler<Event>).onEvent(sessionObject, event)
			} catch (e: Exception) {
				if (log.isLoggable(Level.WARNING)) log.log(Level.WARNING, "Problem on handling event", e)
			}
		}
	}

	fun <T : Event> register(eventType: String = ALL_EVENTS, handler: EventHandler<T>) {
		synchronized(this) {
			var handlers = handlersMap[eventType]
			if (handlers == null) {
				handlers = HashSet()
				handlersMap[eventType] = handlers
			}
			handlers.add(handler)
		}
	}

	fun <T : Event> register(eventType: String = ALL_EVENTS, handler: (SessionObject, T) -> Unit) {
		register(eventType, object : EventHandler<T> {
			override fun onEvent(sessionObject: SessionObject, event: T) {
				handler.invoke(sessionObject, event)
			}
		})
	}

	fun unregister(eventType: String = ALL_EVENTS, handler: EventHandler<*>) {
		synchronized(this) {
			val handlers = handlersMap[eventType]
			if (handlers != null) {
				handlers.remove(handler)
//				if (handlers.isEmpty()) {
//					handlersMap.remove(eventType)
//				}
			}
		}
	}

	fun unregister(handler: EventHandler<*>) {
		synchronized(this) {
			for ((eventType, handlers) in handlersMap) {
				handlers.remove(handler)
			}
		}
	}

}