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


