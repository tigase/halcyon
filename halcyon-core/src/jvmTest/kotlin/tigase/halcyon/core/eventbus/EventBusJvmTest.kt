/*
 * halcyon-core
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

import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.builder.createConfiguration
import tigase.halcyon.core.eventbus.EventBusInterface.Companion.ALL_EVENTS
import tigase.halcyon.core.xmpp.toBareJID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventBusJvmTest {

	@Test
	fun testBasic() {

		val halcyon = Halcyon(createConfiguration {
			account {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
		})
		val eventBus = EventBus(halcyon)
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

		assertTrue(responses.contains("01"))
		assertTrue(responses.contains("02"))
		assertTrue(responses.contains("03"))
		assertTrue(responses.contains("04"))
		assertTrue(responses.contains("05"))
		assertFalse(responses.contains("06"))

		eventBus.unregister(handler)

		eventBus.fire(TestEvent("06"))
		assertFalse(responses.contains("06"))

		eventBus.register(ALL_EVENTS, handler)

		eventBus.fire(TestEvent("07"))
		assertTrue(responses.contains("07"))

	}

	internal class TestEvent(val value: String?) : Event(TYPE) { companion object {

		const val TYPE = "test"
	}

	}

}
