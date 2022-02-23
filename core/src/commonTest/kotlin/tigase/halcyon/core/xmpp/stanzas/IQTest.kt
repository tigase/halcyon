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
package tigase.halcyon.core.xmpp.stanzas

import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class IQTest {

	@Test
	fun testTypeUnavailable() {
		val e = element("iq") {
			attribute("type", "set")
		}
		val p = wrap<IQ>(e)
		assertEquals(IQType.Set, p.type)
	}

	@Test
	fun testTypeSet() {
		val p = iq { type = IQType.Error }
		assertEquals(IQType.Error, p.type)
		assertEquals("error", p.attributes["type"])
		p.type = IQType.Get
		assertEquals(IQType.Get, p.type)
		assertEquals("get", p.attributes["type"])
	}

	@Test
	fun testTypeNull() {
		val e = element("iq") { }
		val p = wrap<IQ>(e)
		try {
			p.type
			fail("Exception should be throw")
		} catch (e: XMPPException) {
			assertEquals(ErrorCondition.BadRequest, e.condition)
		}
	}

	@Test
	fun testTypeUnknown() {
		val e = element("iq") {
			attribute("type", "x")
		}
		val p = wrap<IQ>(e)
		try {
			p.type
			fail("Exception should be throw")
		} catch (e: XMPPException) {
			assertEquals(ErrorCondition.BadRequest, e.condition)
		}
	}

}