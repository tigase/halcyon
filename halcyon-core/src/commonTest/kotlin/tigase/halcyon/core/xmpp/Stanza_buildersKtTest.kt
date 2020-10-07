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