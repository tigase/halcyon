package tigase.halcyon.core.modules

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModulesManagerTest {

	class Module01 : tigase.halcyon.core.modules.XmppModule {
		override val type = "Module01"
		override lateinit var context: tigase.halcyon.core.Context
		override val criteria: tigase.halcyon.core.modules.Criteria = tigase.halcyon.core.modules.Criterion.name("iq")
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
		}
		mm.register(Module01())
		mm.register(Module02())

		assertTrue(arrayOf("1", "2", "a", "b").sortedArray() contentDeepEquals mm.getAvailableFeatures().sortedArray())

		assertEquals(0, mm.getModulesFor(element("presence") {}).size)
		assertEquals(1, mm.getModulesFor(element("iq") {}).size)
		assertEquals(1, mm.getModulesFor(element("msg") {}).size)
		assertEquals("Module01", mm.getModulesFor(element("iq") {}).first().type)

	}

}