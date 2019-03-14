package tigase.halcyon.core.eventbus

abstract class AbstractEventBus(val sessionObject: tigase.halcyon.core.SessionObject) {

	companion object {
		const val ALL_EVENTS = "EventBus#ALL_EVENTS"
	}

	protected val log = tigase.halcyon.core.logger.Logger("tigase.halcyon.core.eventbus.EventBus")

	protected var handlersMap: MutableMap<String, MutableSet<tigase.halcyon.core.eventbus.EventHandler<*>>> =
		createHandlersMap()

	protected abstract fun createHandlersMap(): MutableMap<String, MutableSet<tigase.halcyon.core.eventbus.EventHandler<*>>>

	protected abstract fun createHandlersSet(): MutableSet<tigase.halcyon.core.eventbus.EventHandler<*>>

	private fun getHandlers(eventType: String): Collection<tigase.halcyon.core.eventbus.EventHandler<*>> {
		val result = HashSet<tigase.halcyon.core.eventbus.EventHandler<*>>()

		val a = handlersMap[tigase.halcyon.core.eventbus.AbstractEventBus.Companion.ALL_EVENTS]
		if (a != null && a.isNotEmpty()) {
			result.addAll(a)
		}

		val h = handlersMap[eventType]
		if (h != null && h.isNotEmpty()) {
			result.addAll(h)
		}

		return result
	}

	fun fire(event: tigase.halcyon.core.eventbus.Event) {
		val handlers = getHandlers(event.type)

		fire(event, handlers)
	}

	protected open fun fire(
		event: tigase.halcyon.core.eventbus.Event,
		handlers: Collection<tigase.halcyon.core.eventbus.EventHandler<*>>
	) {
		if (log.isLoggable(tigase.halcyon.core.logger.Level.FINEST)) {
			log.finest("Firing event $event with ${handlers.size} handlers")
		}
		handlers.forEach { eventHandler ->
			try {
				(eventHandler as tigase.halcyon.core.eventbus.EventHandler<tigase.halcyon.core.eventbus.Event>).onEvent(
					sessionObject,
					event
				)
			} catch (e: Exception) {
				if (log.isLoggable(tigase.halcyon.core.logger.Level.WARNING)) log.log(
					tigase.halcyon.core.logger.Level.WARNING,
					"Problem on handling event",
					e
				)
			}
		}
	}

	fun <T : tigase.halcyon.core.eventbus.Event> register(
		eventType: String = tigase.halcyon.core.eventbus.AbstractEventBus.Companion.ALL_EVENTS,
		handler: tigase.halcyon.core.eventbus.EventHandler<T>
	) {
		synchronized(this) {
			var handlers = handlersMap[eventType]
			if (handlers == null) {
				handlers = createHandlersSet()
				handlersMap[eventType] = handlers
			}
			handlers.add(handler)
		}
	}

	fun <T : tigase.halcyon.core.eventbus.Event> register(
		eventType: String = tigase.halcyon.core.eventbus.AbstractEventBus.Companion.ALL_EVENTS,
		handler: (tigase.halcyon.core.SessionObject, T) -> Unit
	) {
		register(eventType, object : tigase.halcyon.core.eventbus.EventHandler<T> {
			override fun onEvent(sessionObject: tigase.halcyon.core.SessionObject, event: T) {
				handler.invoke(sessionObject, event)
			}
		})
	}

	fun unregister(
		eventType: String = tigase.halcyon.core.eventbus.AbstractEventBus.Companion.ALL_EVENTS,
		handler: tigase.halcyon.core.eventbus.EventHandler<*>
	) {
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

	fun unregister(handler: tigase.halcyon.core.eventbus.EventHandler<*>) {
		synchronized(this) {
			for ((eventType, handlers) in handlersMap) {
				handlers.remove(handler)
			}
		}
	}

}