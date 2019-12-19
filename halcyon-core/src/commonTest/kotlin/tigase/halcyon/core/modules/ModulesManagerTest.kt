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
package tigase.halcyon.core.modules

import tigase.halcyon.core.requests.RequestBuilderFactory
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModulesManagerTest {

	class Module01 : tigase.halcyon.core.modules.XmppModule {
		override val type = "Module01"
		override lateinit var context: tigase.halcyon.core.Context
		override val criteria: tigase.halcyon.core.modules.Criteria =
			tigase.halcyon.core.modules.Criterion.name("iq")
		override val features: Array<String> = arrayOf("1", "2")

		override fun initialize() {
		}

		override fun process(element: Element) {
		}
	}

	class Module02 : tigase.halcyon.core.modules.XmppModule {
		override val type = "Module02"
		override lateinit var context: tigase.halcyon.core.Context
		override val criteria = tigase.halcyon.core.modules.Criterion.name("msg")
		override val features = arrayOf("a", "b")

		override fun initialize() {
		}

		override fun process(element: Element) {
		}
	}

	@Test
	fun test01() {
		val mm = tigase.halcyon.core.modules.ModulesManager()
		mm.context = object : tigase.halcyon.core.Context {

			override val eventBus: tigase.halcyon.core.eventbus.EventBus
				get() = TODO("not implemented")
			override val sessionObject: tigase.halcyon.core.SessionObject
				get() = TODO("not implemented")
			override val writer: tigase.halcyon.core.PacketWriter
				get() = TODO("not implemented")
			override val modules: tigase.halcyon.core.modules.ModulesManager
				get() = TODO("not implemented")
			override val request: RequestBuilderFactory
				get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
		}
		mm.register(Module01())
		mm.register(Module02())

		assertTrue(
			arrayOf(
				"1", "2", "a", "b"
			).sortedArray() contentDeepEquals mm.getAvailableFeatures().sortedArray()
		)

		assertEquals(0, mm.getModulesFor(element("presence") {}).size)
		assertEquals(1, mm.getModulesFor(element("iq") {}).size)
		assertEquals(1, mm.getModulesFor(element("msg") {}).size)
		assertEquals("Module01", mm.getModulesFor(element("iq") {}).first().type)

	}

}