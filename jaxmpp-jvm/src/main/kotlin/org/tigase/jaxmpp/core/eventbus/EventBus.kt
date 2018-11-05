package org.tigase.jaxmpp.core.eventbus

import org.tigase.jaxmpp.core.SessionObject
import org.tigase.jaxmpp.core.logger.Level
import java.util.concurrent.Executors

actual class EventBus actual constructor(sessionObject: SessionObject) : AbstractEventBus(sessionObject) {

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

	private fun fireNoThread(event: Event, handlers: Collection<EventHandler<*>>) {
		handlers.forEach { eventHandler ->
			try {
				(eventHandler as EventHandler<Event>).onEvent(sessionObject, event)
			} catch (e: Exception) {
				if (log.isLoggable(Level.WARNING)) log.log(Level.WARNING, "Problem on handling event", e)
			}
		}
	}

	private fun fireThreadPerEvent(event: Event, handlers: Collection<EventHandler<*>>) {
		executor.execute {
			handlers.forEach { eventHandler ->
				try {
					(eventHandler as EventHandler<Event>).onEvent(sessionObject, event)
				} catch (e: Exception) {
					if (log.isLoggable(Level.WARNING)) log.log(Level.WARNING, "Problem on handling event", e)
				}
			}
		}
	}

	private fun fireThreadPerHandler(event: Event, handlers: Collection<EventHandler<*>>) {
		handlers.forEach { eventHandler ->
			executor.execute {
				try {
					(eventHandler as EventHandler<Event>).onEvent(sessionObject, event)
				} catch (e: Exception) {
					if (log.isLoggable(Level.WARNING)) log.log(Level.WARNING, "Problem on handling event", e)
				}
			}
		}
	}

	override fun fire(event: Event, handlers: Collection<EventHandler<*>>) {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Firing event $event with ${handlers.size} handlers")
		}

		when (mode) {
			Mode.NoThread -> fireNoThread(event, handlers)
			Mode.ThreadPerEvent -> fireThreadPerEvent(event, handlers)
			Mode.ThreadPerHandler -> fireThreadPerHandler(event, handlers)
		}

	}

}
