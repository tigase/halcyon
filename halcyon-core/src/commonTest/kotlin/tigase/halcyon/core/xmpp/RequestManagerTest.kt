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

import getIdAttr
import getToAttr
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.requests.RequestsManager
import tigase.halcyon.core.requests.ResponseHandler
import tigase.halcyon.core.requests.Result
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import kotlin.test.*

class RequestManagerTest {

	@Test
	fun testSuccessHandler01() {
		val rm = RequestsManager()

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0

		rm.register(create(e)).response(object : ResponseHandler<Any> {
			override fun success(request: Request<Any>, responseStanza: Element, v: Any?) {
				++successCounter
			}

			override fun error(request: Request<Any>, responseStanza: Element, errorCondition: ErrorCondition) {
				fail()
			}

			override fun timeout(request: Request<Any>) {
				fail()
			}

		})

		val resp = element("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		}
		val handler = rm.getRequest(resp)

		assertNotNull(handler)
		handler.responseStanza = resp
		assertEquals(1, successCounter)
	}

	private fun create(element: Element): Request<Any> {
		val id = element.getIdAttr()
			?: throw tigase.halcyon.core.exceptions.HalcyonException("Stanza must contains 'id' attribute")
		val jid = element.getToAttr()
		return Request(jid, id, currentTimestamp(), element)
	}
	
	@Test
	fun testSuccessHandler02() {
		val rm = RequestsManager()

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0

		rm.register(create(e)).handle {
			success { request, element, any -> ++successCounter }
			error { _, _, _ -> fail() }
			timeout { fail() }

		}

		val resp = element("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		}
		val handler = rm.findAndExecute(resp)
		assertTrue(handler)
		assertEquals(1, successCounter)
	}

	@Test
	fun testSuccessHandler03() {
		val rm = RequestsManager()

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0


		rm.register(create(e)).response { request, element, result ->
			when (result) {
				is Result.Success -> ++successCounter
				else -> fail()
			}
		}

		val resp = element("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		}
		val handler = rm.findAndExecute(resp)
		assertTrue(handler)
		assertEquals(1, successCounter)
	}

	@Test
	fun testError() {
		val rm = RequestsManager()

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var errorCounter = 0

		rm.register(create(e)).handle {
			error { request, element, errorCondition ->
				++errorCounter
				assertEquals(ErrorCondition.NotAllowed, errorCondition)
			}
		}

		val resp = element("iq") {
			attribute("id", "1")
			attribute("type", "error")
			attribute("from", "a@b.c")
			element("error") {
				attribute("type", "cancel")
				element("not-allowed") {
					xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
				}
			}
		}
		rm.findAndExecute(resp)
		assertEquals(1, errorCounter)
	}

	@Test
	fun testTimeout() {
		val rm = RequestsManager()

		var counter = 0

		val r1 = rm.register(create(element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}))
		r1.timeoutDelay = 0
		r1.handle { timeout { _ -> ++counter } }

		val r2 = rm.register(create(element("iq") {
			attribute("id", "2")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}))
		r2.handle { timeout { _ -> ++counter } }

		val r3 = rm.register(create(element("message") {
			attribute("id", "3")
			attribute("to", "a@b.c")
		}))
		r3.timeoutDelay = 0
		r3.handle { timeout { _ -> ++counter } }

		rm.findOutdated()

		assertEquals(1, counter)

		assertFalse(rm.findAndExecute(element("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		}))
		assertTrue(rm.findAndExecute(element("iq") {
			attribute("id", "2")
			attribute("type", "result")
			attribute("from", "a@b.c")
		}))

	}

}