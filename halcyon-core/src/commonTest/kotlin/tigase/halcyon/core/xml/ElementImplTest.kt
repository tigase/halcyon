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
package tigase.halcyon.core.xml

import kotlin.test.*

class ElementImplTest {

	val e_a1_1 = element("a") {
		attribute("id", "000")
		attribute("ok", "010")
		"a"{
			xmlns = "xxx"
			+"value"
		}
		"1"{
			attribute("type", "x")
			xmlns = "xxx"
			+"something else"
		}
		"b"{
			attribute("type", "y")
			"ba"{
				"bb"{
					+"99898"
				}
			}
		}
		"a"{
			+"value2"
		}
	} as ElementImpl
	val e_a1_2 = element("a") {
		attribute("id", "000")
		attribute("ok", "010")
		"a"{
			xmlns = "xxx"
			+"value"
		}
		"1"{
			attribute("type", "x")
			xmlns = "xxx"
			+"something else"
		}
		"b"{
			attribute("type", "y")
			"ba"{
				"bb"{
					+"99898"
				}
			}
		}
		"a"{
			+"value2"
		}
	} as ElementImpl
	val e_b1_1 = element("a") {
		attribute("id", "000")
		attribute("ok", "101")
		"a"{
			xmlns = "xxx"
			+"value"
		}
		"1"{
			attribute("type", "x")
			xmlns = "xxx"
			+"something else"
		}
		"b"{
			attribute("type", "y")
			"ba"{
				"bb"{
					+"99898"
				}
			}
		}
		"a"{
			+"value2"
		}
	} as ElementImpl
	val e_c1_1 = element("a") {
		attribute("id", "000")
		attribute("ok", "010")
		"a"{
			xmlns = "xxx"
			+"value"
		}
		"1"{
			attribute("type", "x")
			xmlns = "xxx"
			+"something else"
		}
		"b"{
			attribute("type", "y")
			"ba"{
				"bb"{
					+"99890"
				}
			}
		}
		"a"{
			+"value2"
		}
	} as ElementImpl

	@Test
	fun testFirstChildAndSibling() {
		val a = e_a1_1.getFirstChild("a")
		assertEquals("a", a!!.name)
		val b = a.getNextSibling()
		assertEquals("1", b!!.name)

		assertEquals("a", e_a1_1.getFirstChild()!!.name)
	}

	@Test
	fun testFindChild() {
		assertEquals("1", e_a1_1.findChild("a", "1")!!.name)
		assertEquals("x", e_a1_1.findChild("a", "1")!!.attributes["type"])
		assertEquals("a", e_a1_1.findChild("a", "a")!!.name)
		assertEquals("99898", e_a1_1.findChild("a", "b", "ba", "bb")!!.value)
	}

	@Test
	fun testGetChildAfter() {
		val ch = e_a1_1.getFirstChild("1")
		assertEquals("1", ch!!.name)
		assertEquals("b", e_a1_1.getChildAfter(ch)!!.name)
	}

	@Test
	fun testChildren() {
		val list = e_a1_1.getChildren("a")
		assertEquals(2, list.count())
		assertEquals("a", list[0].name)
		assertEquals("a", list[1].name)
	}

	@Test
	fun testGetChildrenNS() {
		val list = e_a1_1.getChildrenNS("xxx")
		assertEquals(2, list.count())
		assertEquals("a", list[0].name)
		assertEquals("1", list[1].name)
	}

	@Test
	fun testGetChildrenNameNS() {
		val e = e_a1_1.getChildrenNS("a", "xxx")
		assertEquals("a", e!!.name)
	}

	@Test
	fun testEquals() {
		assertEquals(e_a1_1, e_a1_1)
		assertEquals(e_a1_1, e_a1_2)
		assertEquals(e_a1_2, e_a1_1)

		assertNotEquals(e_a1_1, e_b1_1)
		assertNotEquals(e_a1_1, e_c1_1)
		assertNotEquals(e_b1_1, e_c1_1)

		assertTrue(e_a1_1 == e_a1_2)
		assertTrue(e_a1_1.equals(e_a1_2))
		assertTrue(e_a1_2.equals(e_a1_1))

		assertEquals(e_a1_2, e_a1_1)
		assertEquals(e_a1_1, e_a1_1)
		assertEquals(e_a1_1, e_a1_2)

		assertNotEquals(e_a1_2, e_b1_1)
		assertNotEquals(e_a1_1, e_c1_1)
		assertNotEquals(e_b1_1, e_c1_1)

		assertEquals(e_a1_1.hashCode(), e_a1_1.hashCode())
		assertEquals(e_a1_1.hashCode(), e_a1_2.hashCode())
	}

	private fun createElement(): Element {
		val b = ElementBuilder.create("message").attribute("to", "romeo@example.net")
			.attribute("from", "juliet@example.com/balcony").attribute("type", "chat").child("subject")
			.value("I implore you!").up().child("body").value("Wherefore art thou, Romeo?").up().child("thread")
			.value("e0ffe42b28561960c6b12b944a092794b9683a38").up().child("x").value("tigase:offline").xmlns("tigase")
		return b.build()
	}

	@Test
	fun testFindChild2() {
		val element = createElement()

		var nullElement = element.findChild("message", "missing")
		assertNull(nullElement)

		nullElement = element.findChild("x", "body")
		assertNull(nullElement)

		val c = element.findChild("message", "body")
		assertNotNull(c)
		assertEquals("body", c.name)
		assertEquals("Wherefore art thou, Romeo?", c.value)
	}

	@Test
	fun testNextSibling() {
		val element = createElement()
		val c = element.findChild("message", "body")
		assertNotNull(c)
		assertEquals("body", c.name)
		assertEquals("thread", c.getNextSibling()!!.name)
	}

}