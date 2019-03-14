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
package tigase.halcyon.core.xmpp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class JIDTest {

	@Test
	fun testGetBareJid() {
		val jid = JID.parse("a@b/c")
		assertEquals(BareJID("a", "b"), jid.bareJID)
	}

	@Test
	fun testGetDomain() {
		val jid = JID.parse("a@b/c")
		assertEquals("b", jid.domain)
	}

	@Test
	fun testGetLocalpart() {
		val jid = JID.parse("a@b/c")
		assertEquals("a", jid.localpart)
	}

	@Test
	fun testGetResource() {
		val jid = JID.parse("a@b/c")
		assertEquals("c", jid.resource)
	}

	@Test
	fun testinstance() {
		assertEquals(JID.parse("a@b"), JID.parse("a@b"))
		assertEquals(JID.parse("a@b"), JID("a", "b"))
		assertEquals(JID("a", "b"), JID("a", "b"))
		assertEquals(JID.parse("a@b/c"), JID.parse("a@b/c"))
		assertEquals(JID("a", "b", "c"), JID.parse("a@b/c"))

		assertFalse(JID.parse("a@b").equals(JID.parse("a@b/c")))
	}

	@Test
	fun testPercentJids() {
		val jid = JID.parse("-101100311719181%chat.facebook.com@domain.com")

		assertEquals("domain.com", jid.domain)
		assertEquals("-101100311719181%chat.facebook.com", jid.localpart)
		assertNull(jid.resource)
		assertEquals("-101100311719181%chat.facebook.com@domain.com", jid.toString())
	}

	@Test
	fun testToString() {
		var jid = JID.parse("a@b")
		assertEquals("a@b", jid.toString())

		jid = JID.parse("a@b/c")
		assertEquals("a@b/c", jid.toString())
	}
}