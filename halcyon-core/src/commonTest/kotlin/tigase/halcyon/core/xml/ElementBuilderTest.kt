package tigase.halcyon.core.xml

import tigase.halcyon.core.xml.parser.StreamParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ElementBuilderTest {

	private fun createElement2(): Element {
		val b = ElementBuilder.create("message").attribute("to", "romeo@example.net")
			.attribute("from", "juliet@example.com/balcony").attribute("type", "chat").child("subject")
			.value("I implore you!").up().child("body").value("Wherefore art thou, Romeo?").up().child("thread")
			.value("e0ffe42b28561960c6b12b944a092794b9683a38").up().child("x").xmlns("test:urn").child("presence")
			.value("dnd")

		return b.build()
	}

	private fun createElement1(): Element = element("message") {
		attribute("to", "romeo@example.net")
		attribute("from", "juliet@example.com/balcony")
		attribute("type", "chat")
		"body" {
			+"Wherefore art thou, Romeo?"
		}
		"thread"("e0ffe42b28561960c6b12b944a092794b9683a38")
		element("subject") {
			value = "I implore you!"
		}
		"x" {
			xmlns = "test:urn"
			"presence" {
				+"dnd"
			}
		}
	}

	private fun test(element: Element) {
		assertEquals("romeo@example.net", element.attributes["to"])
		assertEquals("juliet@example.com/balcony", element.attributes["from"])
		assertEquals("chat", element.attributes["type"])

		var nullElement = element.findChild("message", "missing")
		assertNull(nullElement)

		nullElement = element.findChild("x", "body")
		assertNull(nullElement)

		val c = element.findChild("message", "body")
		assertNotNull(c)
		assertEquals("body", c.name)
		assertEquals("Wherefore art thou, Romeo?", c.value)
		assertEquals("thread", c.getNextSibling()?.name)

	}

	@Test
	fun testStreamParser() {
		val xml = """<message from="juliet@example.com/balcony" to="romeo@example.net" type="chat">
<subject>I implore you!</subject><body>Wherefore art thou, Romeo?</body>
<thread>e0ffe42b28561960c6b12b944a092794b9683a38</thread>
<x xmlns="test:urn">
<presence>dnd</presence>
</x>
</message>
""".trimIndent()

		var receivedElement: Element? = null
		val parser = object : StreamParser() {
			override fun onParseError(errorMessage: String) {
			}

			override fun onNextElement(element: Element) {
				receivedElement = element
			}

			override fun onStreamClosed() {
			}

			override fun onStreamStarted(attrs: Map<String, String>) {
			}
		}
		parser.parse(xml)

		test(receivedElement!!)
	}

	@Test
	fun testBuilder() {
		test(createElement1())
		test(createElement2())
	}
}