package org.tigase.jaxmpp.core.eventbus

import org.junit.Assert
import org.junit.Test
import org.tigase.jaxmpp.core.SessionObject

class EventBusJvmTest {

	@Test
	fun testBasic() {
		val eventBus = EventBus(SessionObject())
		val responses = mutableListOf<Any>()

		val handler = object : EventHandler<TestEvent> {
			@Override
			override fun onEvent(sessionObject: SessionObject, event: TestEvent) {
				responses.add(event.value!!)
			}
		}

		eventBus.register(TestEvent.TYPE, handler)

		eventBus.fire(TestEvent("01"))
		eventBus.fire(TestEvent("02"))
		eventBus.fire(TestEvent("03"))
		eventBus.fire(TestEvent("04"))
		eventBus.fire(TestEvent("05"))

		Assert.assertTrue(responses.contains("01"))
		Assert.assertTrue(responses.contains("02"))
		Assert.assertTrue(responses.contains("03"))
		Assert.assertTrue(responses.contains("04"))
		Assert.assertTrue(responses.contains("05"))
		Assert.assertFalse(responses.contains("06"))

		eventBus.unregister(handler)

		eventBus.fire(TestEvent("06"))
		Assert.assertFalse(responses.contains("06"))

		eventBus.register(AbstractEventBus.ALL_EVENTS, handler)

		eventBus.fire(TestEvent("07"))
		Assert.assertTrue(responses.contains("07"))

	}

	internal class TestEvent(val value: String?) : Event(TYPE) {
		companion object {

			val TYPE = "test"
		}

	}

}
