package org.tigase.jaxmpp.core.modules

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.PacketWriter
import org.tigase.jaxmpp.core.SessionObject
import org.tigase.jaxmpp.core.eventbus.EventBus
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xml.stanza
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModulesManagerTest {

	class Module01 : XmppModule {
		override val type = "Module01"
		override lateinit var context: Context
		override val criteria: Criteria = Criterion.name("iq")
		override val features: Array<String> = arrayOf("1", "2")

		override fun initialize() {
		}

		override fun process(element: Element) {
		}
	}

	class Module02 : XmppModule {
		override val type = "Module02"
		override lateinit var context: Context
		override val criteria = Criterion.name("msg")
		override val features = arrayOf("a", "b")

		override fun initialize() {
		}

		override fun process(element: Element) {
		}
	}

	@Test
	fun test01() {
		val mm = ModulesManager()
		mm.context = object : Context {
			override val eventBus: EventBus
				get() = TODO("not implemented")
			override val sessionObject: SessionObject
				get() = TODO("not implemented")
			override val writer: PacketWriter
				get() = TODO("not implemented")
			override val modules: ModulesManager
				get() = TODO("not implemented")
		}
		mm.register(Module01())
		mm.register(Module02())

		assertTrue(arrayOf("1", "2", "a", "b").sortedArray() contentDeepEquals mm.getAvailableFeatures().sortedArray())

		assertEquals(0, mm.getModulesFor(stanza("presence") {}).size)
		assertEquals(1, mm.getModulesFor(stanza("iq") {}).size)
		assertEquals(1, mm.getModulesFor(stanza("msg") {}).size)
		assertEquals("Module01", mm.getModulesFor(stanza("iq") {}).first().type)

	}

}