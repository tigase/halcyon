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
package tigase.halcyon.core.xml.parser

import tigase.halcyon.core.xml.Element
import kotlin.test.*

class StreamParserTest {

	data class Result(val element: Element?, val error: Boolean)

	private fun parse(data: String): Result {
		var r: Result? = null
		val parser = object : StreamParser() {
			override fun onParseError(errorMessage: String) {
				println(errorMessage)
				r = Result(null, true)
			}

			override fun onNextElement(element: Element) {
				r = Result(element, false)
			}

			override fun onStreamClosed() {
				fail("Nothing to close")
			}

			override fun onStreamStarted(attrs: Map<String, String>) {
				fail("Nothing to start")
			}
		}

		parser.parse(data)

		return r ?: fail("It cannot be null")
	}

	@Test
	fun testParse0() {
		val input = "<x>ok</x>"
		var tmp = parse(input)
		assertEquals(input, tmp.element!!.getAsString())
	}

	@Test
	fun testParse() {
		val input =
			"<message><body>body</body><html><body><p><em>Wow</em>*, I&apos;m* <span>green</span>with <strong>envy</strong>!</p></body></html></message>"
		var tmp = parse(input)
		assertNotEquals(input, tmp.element!!.getAsString())
	}

	@Test
	fun testEntities() {

		var e = parse("<message from=\"test@example.com\"><body>© §      ∉ ⇒ </body></message>")
		assertFalse(e.error)
		assertEquals("© §      ∉ ⇒ ", e.element!!.findChild("message", "body")!!.value)

		e = parse("<message from=\"test@example.com\"><body>123 - &#123;</body></message>")
		assertFalse(e.error)
		assertEquals("123 - &#123;", e.element!!.findChild("message", "body")!!.value)

		e = parse("<message from=\"test@example.com\" id=\"&a123;\"></message>")
		assertFalse(e.error)
		assertEquals("&a123;", e.element!!.attributes["id"])

		e = parse("<message from=\"test@example.com\" id=\"&#123;\"></message>")
		assertFalse(e.error)
		assertEquals("&#123;", e.element!!.attributes["id"])

		e = parse("<message from=\"test@example.com\"><body>123 - &123;</body></message>")
		assertTrue(e.error)

		e = parse("<message from=\"test@example.com\"><body>123 - &#123</body></message>")
		assertTrue(e.error)

		e = parse("<message from=\"test@example.com\"><body>123 - &a123</body></message>")
		assertTrue(e.error)

		e = parse("<message from=\"test@example.com\" id=\"&123;\"></message>")
		assertTrue(e.error)

		e = parse("<message from=\"test@example.com\" id=\"&a123\"></message>")
		assertTrue(e.error)

		e = parse("<mes&sage from=\"test@example.com\"></message>")
		assertTrue(e.error)

		e = parse("<mes&amp;sage from=\"test@example.com\"></message>")
		assertTrue(e.error)

		e = parse("<message from=\"test@example.com\"><<body>Test</body></message>")
		assertTrue(e.error)

		e = parse("<message from=\"test@example.com\"><body>Test</body1></message>")
		assertTrue(e.error)

		e =
			parse("<message to=\"test@zeus\" type=\"chat\" id=\"t&amp;t<\"><body>Test &amp; done</body></message>")
		assertTrue(e.error)

	}

	@Test
	fun testStreamParser() {
		var tmp = parse("<iq/>")
		assertEquals("iq", tmp.element!!.name)

		tmp = parse("<iq xmlns:ack='http://jabber.org/protocol/ack'><ack:a>9</ack:a></iq>")
		println(tmp.element?.getAsString())
		assertFalse(tmp.error)
		assertEquals("iq", tmp.element!!.name)
		assertEquals("9", tmp.element!!.getChildrenNS("a", "http://jabber.org/protocol/ack")?.value)
	}

}