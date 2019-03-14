package tigase.halcyon.core.eventbus

import kotlin.test.Test

class EventBusTest {

	class Event01 : tigase.halcyon.core.eventbus.Event(TYPE) {
		companion object {
			const val TYPE = "Event01"
		}
	}

	class Event02 : tigase.halcyon.core.eventbus.Event(TYPE) {
		companion object {
			const val TYPE = "Event02"
		}
	}

	class Event03 : tigase.halcyon.core.eventbus.Event(TYPE) {
		companion object {
			const val TYPE = "Event03"
		}
	}

	@Test
	fun testEventBus() {
		val eventBus = tigase.halcyon.core.eventbus.EventBus(tigase.halcyon.core.SessionObject())

		eventBus.register(Event01.TYPE, object : tigase.halcyon.core.eventbus.EventHandler<Event01> {
			override fun onEvent(sessionObject: tigase.halcyon.core.SessionObject, event: Event01) {
				println(event)
			}
		})
		eventBus.register<tigase.halcyon.core.eventbus.Event> { _, event01 ->
			println()
		}

//		eventBus.register("1") { event -> println(event) }

	}

}


