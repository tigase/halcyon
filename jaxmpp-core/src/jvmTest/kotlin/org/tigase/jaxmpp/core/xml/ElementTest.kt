package org.tigase.jaxmpp.core.xml

import junit.framework.TestCase.*
import org.junit.Test

class ElementTest {

	private fun createElement(): Element {
		val b = ElementBuilder.create("message").attribute("to", "romeo@example.net")
			.attribute("from", "juliet@example.com/balcony").attribute("type", "chat").child("subject")
			.value("I implore you!").up().child("body").value("Wherefore art thou, Romeo?").up().child("thread")
			.value("e0ffe42b28561960c6b12b944a092794b9683a38").up().child("x").value("tigase:offline").xmlns("tigase")
		return b.build()
	}

	@Test
	fun testFindChild() {
		val element = createElement()

		var nullElement = element.findChild("message", "missing")
		assertNull(nullElement)

		nullElement = element.findChild("x", "body")
		assertNull(nullElement)

		val c = element.findChild("message", "body")
		assertNotNull(c)
		assertEquals("body", c!!.name)
		assertEquals("Wherefore art thou, Romeo?", c.value)

		System.out.println(element.getAsString())
	}

	@Test
	fun testNextSibling() {
		val element = createElement()
		val c = element.findChild("message", "body")
		assertNotNull(c)
		assertEquals("body", c!!.name)
		assertEquals("thread", c.getNextSibling()!!.name)
	}
}
