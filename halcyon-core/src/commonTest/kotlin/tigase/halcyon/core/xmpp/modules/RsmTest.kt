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
package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.xml.element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RsmTest {

	@Test
	fun testToElement() {
		val rsm1 = RSM.Query(index = 73, before = "b191", after = "a714", max = 99)
		val e1 = rsm1.toElement()

		assertEquals("73", e1.getFirstChild("index")?.value)
		assertEquals("a714", e1.getFirstChild("after")?.value)
		assertEquals("b191", e1.getFirstChild("before")?.value)
		assertEquals("99", e1.getFirstChild("max")?.value)
		assertEquals("http://jabber.org/protocol/rsm", e1.xmlns)
		assertEquals("set", e1.name)
	}

	@Test
	fun testQueryBuilder() {
		val rsm1 = RSM.Query(index = 73, before = "b191", after = "a714", max = 99)
		val rsm2 = RSM.query {
			index(73)
			before("b191")
			after("a714")
			max(99)
		}
		assertEquals(rsm1, rsm2)
	}

	@Test
	fun testQueryBuilderEmptyBefore() {
		val rsm = RSM.query {
			before()
		}
		assertNull(rsm.after)
		assertNotNull(rsm.before)
		assertNull(rsm.index)
		assertNull(rsm.max)

		val e = rsm.toElement()
		assertNotNull(e.getFirstChild("before")) { before ->
			assertNull(before.value)
		}
		assertNull(e.getFirstChild("after"))
		assertNull(e.getFirstChild("index"))
		assertNull(e.getFirstChild("max"))
	}

	@Test
	fun testParseRSM() {
		val e = element("set") {
			xmlns = "http://jabber.org/protocol/rsm"
			"first" {
				attributes["index"] = "1"
				+"stpeter@jabber.org"
			}
			"last" {
				+"peterpan@neverland.lit"
			}
			"count" {
				+"800"
			}
		}
		val rsm = RSM.parseResult(e)

		assertEquals(1, rsm.index)
		assertEquals("stpeter@jabber.org", rsm.first)
		assertEquals("peterpan@neverland.lit", rsm.last)
		assertEquals(800, rsm.count)
	}

}