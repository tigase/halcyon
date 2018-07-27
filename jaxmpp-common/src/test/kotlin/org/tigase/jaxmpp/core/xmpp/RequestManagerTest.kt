package org.tigase.jaxmpp.core.xmpp

import org.tigase.jaxmpp.core.requests.Request
import org.tigase.jaxmpp.core.requests.RequestsManager
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xml.stanza
import kotlin.test.*

class RequestManagerTest {

	@Test
	fun testSuccessHandler01() {
		val rm = RequestsManager()

		val e = stanza("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0

		rm.create(e).response(object : Request.Callback {
			override fun success(request: Request, responseStanza: Element) {
				++successCounter
			}

			override fun error(request: Request, responseStanza: Element, errorCondition: ErrorCondition) {
				fail()
			}

			override fun timeout(request: Request) {
				fail()
			}

		})

		val resp = stanza("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		}
		val handler = rm.getRequest(resp)

		assertNotNull(handler)
		handler!!.responseStanza = resp
		assertEquals(1, successCounter)
	}

	@Test
	fun testSuccessHandler02() {
		val rm = RequestsManager()

		val e = stanza("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0

		rm.create(e).handle {
			success { _, _ -> ++successCounter }
			error { _, _, _ -> fail() }
			timeout { fail() }

		}

		val resp = stanza("iq") {
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

		val e = stanza("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0

		rm.create(e).response { request: Request, element: Element?, result: Request.Result ->
			when (result) {
				is Request.Result.Success -> ++successCounter
				else -> fail()
			}
		}

		val resp = stanza("iq") {
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

		val e = stanza("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var errorCounter = 0

		rm.create(e).handle {
			error { request, element, errorCondition ->
				++errorCounter
				assertEquals(ErrorCondition.not_allowed, errorCondition)
			}
		}

		val resp = stanza("iq") {
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

		val r1 = rm.create(stanza("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		})
		r1.timeoutDelay = 0
		r1.handle { timeout { _ -> ++counter } }

		val r2 = rm.create(stanza("iq") {
			attribute("id", "2")
			attribute("type", "get")
			attribute("to", "a@b.c")
		})
		r2.handle { timeout { _ -> ++counter } }

		val r3 = rm.create(stanza("message") {
			attribute("id", "3")
			attribute("to", "a@b.c")
		})
		r3.timeoutDelay = 0
		r3.handle { timeout { _ -> ++counter } }

		rm.findOutdated()

		assertEquals(1, counter)

		assertFalse(rm.findAndExecute(stanza("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		}))
		assertTrue(rm.findAndExecute(stanza("iq") {
			attribute("id", "2")
			attribute("type", "result")
			attribute("from", "a@b.c")
		}))

	}

}