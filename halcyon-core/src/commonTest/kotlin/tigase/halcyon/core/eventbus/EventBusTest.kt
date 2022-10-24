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
import tigase.halcyon.core.xmpp.toBareJID
import kotlin.test.Test

class EventBusTest {

	class Event01 : Event(TYPE) { companion object {

		const val TYPE = "Event01"
	}
	}

	@Test
	fun testEventBus() {
		val halcyon = Halcyon(createConfiguration {
			account {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
		})
		val eventBus = EventBus(halcyon)

		eventBus.register(Event01.TYPE, object : EventHandler<Event01> {
			override fun onEvent(event: Event01) {
				println(event)
			}
		})
		eventBus.register<Event> { println() }

//		eventBus.register("1") { event -> println(event) }
	}

}


