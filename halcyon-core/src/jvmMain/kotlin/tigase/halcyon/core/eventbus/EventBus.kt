package tigase.halcyon.core.eventbus

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

actual class EventBus actual constructor(sessionObject: tigase.halcyon.core.SessionObject) :
	tigase.halcyon.core.eventbus.AbstractEventBus(sessionObject) {

	override fun createHandlersMap(): MutableMap<String, MutableSet<tigase.halcyon.core.eventbus.EventHandler<*>>> =
		ConcurrentHashMap()

	override fun createHandlersSet(): MutableSet<tigase.halcyon.core.eventbus.EventHandler<*>> =
		ConcurrentHashMap.newKeySet<tigase.halcyon.core.eventbus.EventHandler<*>>()

	enum class Mode {
		NoThread,
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

	private fun fireNoThread(
		event: tigase.halcyon.core.eventbus.Event,
		handlers: Collection<tigase.halcyon.core.eventbus.EventHandler<*>>
	) {
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

	private fun fireThreadPerEvent(
		event: tigase.halcyon.core.eventbus.Event,
		handlers: Collection<tigase.halcyon.core.eventbus.EventHandler<*>>
	) {
		executor.execute {
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
	}

	private fun fireThreadPerHandler(
		event: tigase.halcyon.core.eventbus.Event,
		handlers: Collection<tigase.halcyon.core.eventbus.EventHandler<*>>
	) {
		handlers.forEach { eventHandler ->
			executor.execute {
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
	}

	override fun fire(
		event: tigase.halcyon.core.eventbus.Event,
		handlers: Collection<tigase.halcyon.core.eventbus.EventHandler<*>>
	) {
		if (log.isLoggable(tigase.halcyon.core.logger.Level.FINEST)) {
			log.finest("Firing event $event with ${handlers.size} handlers")
		}

		when (mode) {
			Mode.NoThread -> fireNoThread(event, handlers)
			Mode.ThreadPerEvent -> fireThreadPerEvent(event, handlers)
			Mode.ThreadPerHandler -> fireThreadPerHandler(event, handlers)
		}

	}

}
