package org.tigase.jaxmpp.core.eventbus

import org.tigase.jaxmpp.core.SessionObject
import kotlin.test.Test

class EventBusTest {

	class Event01 : Event(TYPE) {
		companion object {
			const val TYPE = "Event01"
		}
	}

	class Event02 : Event(TYPE) {
		companion object {
			const val TYPE = "Event02"
		}
	}

	class Event03 : Event(TYPE) {
		companion object {
			const val TYPE = "Event03"
		}
	}

	@Test
	fun testEventBus() {
		val eventBus = EventBus(SessionObject())

		eventBus.register(Event01.TYPE, object : EventHandler<Event01> {
			override fun onEvent(sessionObject: SessionObject, event: Event01) {
				println(event)
			}
		})
		eventBus.register<Event> { _, event01 ->
			println()
		}

//		eventBus.register("1") { event -> println(event) }

	}

}


