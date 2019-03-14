package tigase.halcyon.core.xmpp

import kotlin.test.*

class BareJIDTest {

	@Test
	fun testEquals() {
		assertEquals(BareJID("a", "b"), BareJID("a", "b"))
		assertEquals("a@b", BareJID("a", "b").toString())
		assertEquals("a@b", BareJID("a", "b").toString())
		assertEquals("b", BareJID(domain = "b").toString())
		assertNotEquals(BareJID("a", "c"), BareJID("a", "b"))
		assertNotEquals(BareJID("b", "b"), BareJID("a", "b"))

		assertTrue(BareJID("a", "b") == BareJID.parse("a@b"))
		assertFalse(BareJID("b", "b") == BareJID.parse("a@b"))

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