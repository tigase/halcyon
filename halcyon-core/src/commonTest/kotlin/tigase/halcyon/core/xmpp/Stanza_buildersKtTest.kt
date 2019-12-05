package tigase.halcyon.core.xmpp

import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xml.ElementImpl
import tigase.halcyon.core.xmpp.stanzas.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class Stanza_buildersKtTest {


	@Test
	fun testWrap() {
		assertTrue { wrap<Stanza<*>>(ElementImpl("iq")) is IQ }
		assertTrue { wrap<Stanza<*>>(ElementImpl("message")) is Message }
		assertTrue { wrap<Stanza<*>>(ElementImpl("presence")) is Presence }

		try {
			assertTrue { wrap<Stanza<*>>(ElementImpl("UNKNOWN")) is IQ }
			fail("Should throw exception!")
		} catch (e: HalcyonException) {
		}

		assertFalse { wrap<Stanza<*>>(ElementImpl("presence")) is IQ }
	}
}