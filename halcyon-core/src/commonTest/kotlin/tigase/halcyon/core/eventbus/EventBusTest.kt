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

import kotlin.test.Test
import kotlin.test.assertNotNull
import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.builder.createConfiguration
import tigase.halcyon.core.xmpp.toBareJID

class EventBusTest {

    class Event01 : Event(TYPE) {

        companion object : EventDefinition<Event01> {

            override val TYPE = "Event01"
        }
    }

    @Test
    fun testEventBus() {
        val halcyon = Halcyon(
            createConfiguration {
                auth {
                    userJID = "user@example.com".toBareJID()
                    password { "pencil" }
                }
            }
        )
        val eventBus = EventBus(halcyon)

        var resultH1: Event01? = null
        var resultH2: Event01? = null
        var resultH3: Event01? = null

        eventBus.register(
            Event01.TYPE,
            object : EventHandler<Event01> {
                override fun onEvent(event: Event01) {
                    resultH1 = event
                }
            }
        )
        eventBus.register<Event01>(Event01.TYPE) { resultH2 = it }
        eventBus.register(Event01) { resultH3 = it }

        eventBus.fire(Event01())

        assertNotNull(resultH1)
        assertNotNull(resultH2)
        assertNotNull(resultH3)
    }
}
