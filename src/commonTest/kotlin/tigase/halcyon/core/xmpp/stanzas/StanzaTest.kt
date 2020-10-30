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
package tigase.halcyon.core.xmpp.stanzas

import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.*

class StanzaTest {

	@Test
	fun equalsTestAndHashCode() {
		val s1 = iq {
			to = "a@b.c/d".toJID()
			"x"{
				xmlns = "1:2:3"
			}
		}
		val e1 = element("iq") {
			attribute("id", s1.attributes["id"]!!)
			attribute("to", "a@b.c/d")
			"x"{
				xmlns = "1:2:3"
			}
		}

		val e2 = element("iq") {
			attribute("id", s1.attributes["id"]!!)
			attribute("to", "a@b.c/d")
			"x"{
				xmlns = "1:2:3"
				+"x"
			}
		}

		assertTrue(s1.equals(e1))
		assertTrue(e1.equals(s1))

		assertNotSame(s1, e1)
		assertEquals(s1, e1)

		assertNotSame(s1, e2)
		assertNotEquals(e1, e2)

		assertEquals(s1.hashCode(), s1.hashCode())
		assertEquals(s1.hashCode(), e1.hashCode())
	}

	@Test
	fun testTo() {
		val e = element("message") {
			attribute("to", "aaa@bb.c/d")
		}
		val s = wrap<Message>(e)
		assertEquals(JID.parse("aaa@bb.c/d"), s.to)
		s.to = "plll@qa.pl/sss".toJID()
		assertEquals(JID.parse("plll@qa.pl/sss"), s.to)
		assertEquals("plll@qa.pl/sss", s.attributes["to"])
	}

	@Test
	fun testFrom() {
		val e = element("message") {
			attribute("from", "aaa@bb.c/d")
		}
		val s = wrap<Message>(e)
		assertEquals(JID.parse("aaa@bb.c/d"), s.from)
		s.from = "plll@qa.pl/sss".toJID()
		assertEquals(JID.parse("plll@qa.pl/sss"), s.from)
		assertEquals("plll@qa.pl/sss", s.attributes["from"])
	}

}