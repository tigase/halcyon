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
package tigase.halcyon.core.xmpp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BareJIDTest {

	@Test
	fun testEquals() {
		assertEquals(BareJID("a", "b"), BareJID("a", "b"))
		assertEquals("a@b", BareJID("a", "b").toString())
		assertEquals("a@b", BareJID("a", "b").toString())
		assertEquals("b", BareJID(domain = "b").toString())
		assertNotEquals(BareJID("a", "c"), BareJID("a", "b"))
		assertNotEquals(BareJID("b", "b"), BareJID("a", "b"))

		assertEquals(BareJID("a", "b"), BareJID.parse("a@b"))
		assertNotEquals(BareJID("b", "b"), BareJID.parse("a@b"))

		assertEquals(BareJID.parse("a@b"), BareJID.parse("a@b"))
		assertEquals(BareJID.parse("a@b"), BareJID("a", "b"))
		assertEquals(BareJID("a", "b"), BareJID("a", "b"))
		assertEquals(BareJID.parse("a@b"), BareJID.parse("a@b/c"))

		assertEquals("b", BareJID.parse("a@b").domain)
		assertEquals("a", BareJID.parse("a@b").localpart)
	}

	@Test
	fun testPercentJids() {
		val jid = BareJID.parse("-101100311719181%chat.facebook.com@domain.com")

		assertEquals("domain.com", jid.domain)
		assertEquals("-101100311719181%chat.facebook.com", jid.localpart)
		assertEquals("-101100311719181%chat.facebook.com@domain.com", jid.toString())

		assertEquals(jid, jid.copy())
	}

	@Test
	fun testToString() {
		var jid = BareJID.parse("a@b")
		assertEquals("a@b", jid.toString())

		jid = BareJID.parse("a@b/c")
		assertEquals("a@b", jid.toString())

		jid = BareJID.parse("b")
		assertEquals("b", jid.domain)
		assertNull(jid.localpart)
		assertEquals("b", jid.toString())
	}

}