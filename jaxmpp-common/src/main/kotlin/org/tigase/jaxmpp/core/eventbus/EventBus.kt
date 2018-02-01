package org.tigase.jaxmpp.core.eventbus

expect fun createHandlersMap(): MutableMap<String, MutableCollection<EventHandler<*>>>
expect fun createHandlersList(): MutableCollection<EventHandler<*>>

open class EventBus {

	companion object {
		const val ALL_EVENTS = "EventBus#ALL_EVENTS"
	}

	var handlersMap = createHandlersMap()

	private fun getHandlers(eventType: String): Collection<EventHandler<*>> {
		val result = createHandlersList()

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
		try {
			handlers.forEach { eventHandler -> (eventHandler as EventHandler<Event>).onEvent(event) }
		} catch (e: Exception) {
			println(e)
		}
	}

	fun <T : Event> register(eventType: String = ALL_EVENTS, handler: EventHandler<T>) {
		synchronized(this) {
			var handlers = handlersMap[eventType]
			if (handlers == null) {
				handlers = createHandlersList()
				handlersMap[eventType] = handlers
			}
			handlers.add(handler)
		}
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

fun <T : Event> EventBus.register(eventType: String = EventBus.ALL_EVENTS, handler: (T) -> Unit) {
	register(eventType, object : EventHandler<T> {
		override fun onEvent(event: T) {
			handler.invoke(event)
		}
	})
}