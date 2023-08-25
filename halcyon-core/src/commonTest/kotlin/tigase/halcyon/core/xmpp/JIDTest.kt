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

import kotlin.test.*

class JIDTest {

	@Test
	fun testToJID() {
		assertIs<FullJID>("a@b.c/1".toJID())
		assertIs<FullJID>("b.c/1".toJID())
		assertIs<BareJID>("a@b.c".toJID())
		assertIs<BareJID>("b.c".toJID())
	}

	@Test
	fun testHashCode() {
		assertEquals("a@b.c".toJID().hashCode(), "a@b.c".toJID().hashCode())
		assertEquals("a@b.c/d".toJID().hashCode(), "a@b.c/d".toJID().hashCode())
		assertEquals("a@b.c".toJID().hashCode(), "a@b.c/d".toJID().hashCode())
		assertEquals("a@b.c/d".toJID().hashCode(), "a@b.c".toJID().hashCode())
	}

	@Test
	fun testJIDcomparator() {
		assertTrue {
			val x = "a@b.c".toJID()
			equalsJID(x, x)
		}
		assertTrue {
			val x = "a@b.c/12".toJID()
			equalsJID(x, x)
		}
		assertTrue { equalsJID("a@b.c".toJID(), "a@b.c".toJID()) }
		assertTrue { equalsJID("a@b.c/12".toJID(), "a@b.c/12".toJID()) }
		assertTrue { equalsJID("a@b.c/12".toBareJID(), "a@b.c".toJID()) }
		assertTrue { equalsJID("a@b.c".toJID(), "a@b.c/12".toBareJID()) }
	}

	@Test
	fun testEquals() {
		assertEquals(BareJID("a", "b"), BareJID("a", "b"))
		assertEquals("a@b", BareJID("a", "b").toString())
		assertEquals("a@b", BareJID("a", "b").toString())
		assertEquals("b", BareJID(domain = "b").toString())
		assertNotEquals(BareJID("a", "c"), BareJID("a", "b"))
		assertNotEquals(BareJID("b", "b"), BareJID("a", "b"))

		assertEquals(BareJID("a", "b"), "a@b".toBareJID())
		assertNotEquals(BareJID("b", "b"), "a@b".toBareJID())

		assertEquals("a@b".toBareJID(), "a@b".toBareJID())
		assertEquals("a@b".toBareJID(), BareJID("a", "b"))
		assertEquals(BareJID("a", "b"), BareJID("a", "b"))
		assertEquals("a@b".toBareJID(), "a@b/c".toBareJID())

		assertEquals("b", "a@b".toBareJID().domain)
		assertEquals("a", "a@b".toBareJID().localpart)

		val jid = "a@b.c/12".toJID()
		val jidF = "a@b.c/12".toFullJID()
		val jidB = "a@b.c/12".toBareJID()
		assertEquals(jid, jid)
		assertEquals(jid, jidF)
		assertEquals(jid, jidB)
		assertEquals(jidF, jid)
		assertEquals(jidF, jidF)
		assertEquals(jidB, jid)
		assertEquals(jidB, jidB)

		assertEquals("a@b.c".toJID(), "a@b.c/12".toJID())
		assertEquals("a@b.c/12".toJID(), "a@b.c/12".toJID())
		assertEquals("a@b.c/12".toJID(), "a@b.c".toJID())
		assertEquals("b.c/12".toJID(), "b.c".toJID())
		assertEquals("b.c".toJID(), "b.c/12".toJID())
		assertEquals("b.c".toJID(), "b.c".toJID())

		assertNotEquals("a@b.c".toJID(), "a@b.d".toJID())
		assertNotEquals("a@b.c/2".toJID(), "a@b.c/1".toJID())
		assertNotEquals("b@b.c/1".toJID(), "a@b.c/1".toJID())
		assertNotEquals("b@b.c/2".toJID(), "a@b.c/2".toJID())

		assertNotEquals("b.c".toJID(), "a@b.c".toJID())
		assertNotEquals("a@b.c".toJID(), "b.c".toJID())
		assertNotEquals("b.c/12".toJID(), "b.c/13".toJID())
		assertNotEquals("a@b.c/12".toJID(), "b.c/13".toJID())
		assertNotEquals("a@b.c/12".toJID(), "b.c".toJID())
	}

	@Test
	fun testPercentBareJids() {
		val jid = "-101100311719181%chat.facebook.com@domain.com".toBareJID()

		assertEquals("domain.com", jid.domain)
		assertEquals("-101100311719181%chat.facebook.com", jid.localpart)
		assertEquals("-101100311719181%chat.facebook.com@domain.com", jid.toString())

		assertEquals(jid, jid.copy())
	}

	@Test
	fun testBareJidToString() {
		var jid = "a@b".toBareJID()
		assertEquals("a@b", jid.toString())

		jid = "a@b/c".toBareJID()
		assertEquals("a@b", jid.toString())

		jid = "b".toBareJID()
		assertEquals("b", jid.domain)
		assertNull(jid.localpart)
		assertEquals("b", jid.toString())
	}


	@Test
	fun testGetBareJid() {
		val jid = "a@b/c".toFullJID()
		assertEquals(BareJID("a", "b"), jid.bareJID)
	}

	@Test
	fun testGetDomain() {
		val jid = "a@b/c".toFullJID()
		assertEquals("b", jid.domain)
	}

	@Test
	fun testGetLocalpart() {
		val jid = "a@b/c".toFullJID()
		assertEquals("a", jid.localpart)
	}

	@Test
	fun testGetResource() {
		val jid = "a@b/c".toFullJID()
		assertEquals("c", jid.resource)
	}

	@Test
	fun testinstance() {
		assertEquals("a@b".toFullJID(), "a@b".toFullJID())
		assertEquals("a@b".toFullJID(), FullJID("a", "b"))
		assertEquals(FullJID("a", "b"), FullJID("a", "b"))
		assertEquals("a@b/c".toFullJID(), "a@b/c".toFullJID())
		assertEquals(FullJID("a", "b", "c"), "a@b/c".toFullJID())

		assertFalse("a@b".toFullJID().equals("a@b/c".toFullJID()))
	}

	@Test
	fun testPercentJids() {
		val jid = "-101100311719181%chat.facebook.com@domain.com".toFullJID()

		assertEquals("domain.com", jid.domain)
		assertEquals("-101100311719181%chat.facebook.com", jid.localpart)
		assertNull(jid.resource)
		assertEquals("-101100311719181%chat.facebook.com@domain.com", jid.toString())
	}

	@Test
	fun testToString() {
		var jid = "a@b".toFullJID()
		assertEquals("a@b", jid.toString())

		jid = "a@b/c".toFullJID()
		assertEquals("a@b/c", jid.toString())
	}
}