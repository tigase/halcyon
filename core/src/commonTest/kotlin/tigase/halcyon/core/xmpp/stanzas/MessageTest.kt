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
import kotlin.test.assertNull
import kotlin.test.fail

class MessageTest {

	@Test
	fun testTypeUnavailable() {
		val e = element("message") {
			attribute("type", "chat")
		}
		val p = wrap<Message>(e)
		assertEquals(MessageType.Chat, p.type)
	}

	@Test
	fun testTypeNull() {
		val e = element("message") { }
		val p = wrap<Message>(e)
		assertNull(p.type)
	}

	@Test
	fun testBodySet() {
		val e = message {
			"body"{ +"192562" }
		}
		assertEquals("192562", e.body)
		e.body = "9876"
		assertEquals("9876", e.body)
		assertEquals("9876", e.getFirstChild("body")?.value)
	}

	@Test
	fun testTypeSet() {
		val p = message { type = MessageType.Error }
		assertEquals(MessageType.Error, p.type)
		assertEquals("error", p.attributes["type"])
		p.type = MessageType.Groupchat
		assertEquals(MessageType.Groupchat, p.type)
		assertEquals("groupchat", p.attributes["type"])
		p.type = null
		assertNull(p.type)
		assertNull(p.attributes["type"])
	}

	@Test
	fun testTypeUnknown() {
		val e = element("message") {
			attribute("type", "x")
		}
		val p = wrap<Message>(e)
		try {
			p.type
			fail("Exception should be throw")
		} catch (e: XMPPException) {
			assertEquals(ErrorCondition.BadRequest, e.condition)
		}
	}

}