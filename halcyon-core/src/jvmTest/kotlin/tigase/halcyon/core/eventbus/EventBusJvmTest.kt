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

import org.junit.Assert
import org.junit.Test
import tigase.halcyon.core.SessionObject
import tigase.halcyon.core.eventbus.AbstractEventBus.Companion.ALL_EVENTS

class EventBusJvmTest {

	@Test
	fun testBasic() {
		val sessionObject = SessionObject()
		val eventBus = EventBus(sessionObject)
		val responses = mutableListOf<Any>()

		val handler = object : EventHandler<TestEvent> {
			@Override
			override fun onEvent(event: TestEvent) {
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

		eventBus.register(ALL_EVENTS, handler)

		eventBus.fire(TestEvent("07"))
		Assert.assertTrue(responses.contains("07"))

	}

	internal class TestEvent(val value: String?) : Event(TYPE) {
		companion object {
			const val TYPE = "test"
		}

	}

}
