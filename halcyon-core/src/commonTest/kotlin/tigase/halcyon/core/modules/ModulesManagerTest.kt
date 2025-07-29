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
package tigase.halcyon.core.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tigase.halcyon.core.Context
import tigase.halcyon.core.configuration.Configuration
import tigase.halcyon.core.requests.RequestBuilderFactory
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.FullJID
import tigase.halcyon.core.xmpp.modules.auth.SASLContext

class ModulesManagerTest {

    class Module01(override val context: Context) : XmppModule {

        override val type = "Module01"
        override val criteria: Criteria = Criterion.name("iq")
        override val features: Array<String> = arrayOf("1", "2")

        override fun process(element: Element) {
        }
    }

    class Module02(override val context: Context) : XmppModule {

        override val type = "Module02"
        override val criteria = Criterion.name("msg")
        override val features = arrayOf("a", "b")

        override fun process(element: Element) {
        }
    }

    @Test
    fun test01() {
        val mm = ModulesManager()
        mm.context = object : Context {

            override val eventBus: tigase.halcyon.core.eventbus.EventBus
                get() = TODO("not implemented")
            override val config: Configuration
                get() = TODO("not implemented") // To change initializer of created properties use File | Settings | File Templates.
            override val writer: tigase.halcyon.core.PacketWriter
                get() = TODO("not implemented")
            override val modules: ModulesManager
                get() = TODO("not implemented")
            override val request: RequestBuilderFactory
                get() = TODO("not implemented") // To change initializer of created properties use File | Settings | File Templates.
            override val authContext: SASLContext
                get() = TODO("Not yet implemented")
            override val boundJID: FullJID
                get() = TODO("Not yet implemented")
        }
        mm.register(Module01(mm.context))
        mm.register(Module02(mm.context))

        assertTrue(
            arrayOf("1", "2", "a", "b").sortedArray() contentDeepEquals
                mm.getAvailableFeatures().sortedArray()
        )

        assertEquals(0, mm.getModulesFor(element("presence") {}).size)
        assertEquals(1, mm.getModulesFor(element("iq") {}).size)
        assertEquals(1, mm.getModulesFor(element("msg") {}).size)
        assertEquals(
            "Module01",
            mm.getModulesFor(element("iq") {}).first().type
        )
    }
}
