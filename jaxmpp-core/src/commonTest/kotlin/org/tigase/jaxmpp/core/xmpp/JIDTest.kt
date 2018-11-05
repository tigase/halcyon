package org.tigase.jaxmpp.core.xmpp

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class JIDTest {

	fun testGetBareJid() {
		val jid = JID.parse("a@b/c")
		assertEquals(BareJID("a", "b"), jid.bareJID)
	}

	fun testGetDomain() {
		val jid = JID.parse("a@b/c")
		assertEquals("b", jid.domain)
	}

	fun testGetLocalpart() {
		val jid = JID.parse("a@b/c")
		assertEquals("a", jid.localpart)
	}

	fun testGetResource() {
		val jid = JID.parse("a@b/c")
		assertEquals("c", jid.resource)
	}

	fun testinstance() {
		assertEquals(JID.parse("a@b"), JID.parse("a@b"))
		assertEquals(JID.parse("a@b"), JID("a", "b"))
		assertEquals(JID("a", "b"), JID("a", "b"))
		assertEquals(JID.parse("a@b/c"), JID.parse("a@b/c"))
		assertEquals(JID("a", "b", "c"), JID.parse("a@b/c"))

		assertFalse(JID.parse("a@b").equals(JID.parse("a@b/c")))
	}

	fun testPercentJids() {
		val jid = JID.parse("-101100311719181%chat.facebook.com@domain.com")

		assertEquals("domain.com", jid.domain)
		assertEquals("-101100311719181%chat.facebook.com", jid.localpart)
		assertNull(jid.resource)
		assertEquals("-101100311719181%chat.facebook.com@domain.com", jid.toString())
	}

	fun testToString() {
		var jid = JID.parse("a@b")
		assertEquals("a@b", jid.toString())

		jid = JID.parse("a@b/c")
		assertEquals("a@b/c", jid.toString())
	}
}