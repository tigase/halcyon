package org.tigase.jaxmpp.core.xmpp

import org.tigase.jaxmpp.core.AsyncCallback
import org.tigase.jaxmpp.core.responsemanager.ResponseManager
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xml.stanza
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class ResponseManagerTest {

	@Test
	fun testSuccessHandler01() {
		val rm = ResponseManager()

		val e = stanza("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0;

		rm.registerRequest(e, object : AsyncCallback {
			override fun oSuccess(responseStanza: Element) {
				++successCounter
			}

			override fun onError(responseStanza: Element, condition: ErrorCondition) {
				fail()
			}

			override fun onTimeout() {
				fail()
			}
		});

		val resp = stanza("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		};
		val handler = rm.getHandler(resp)

		assertNotNull(handler)
		rm.run(handler!!, resp)
		assertEquals(1, successCounter)
	}

	@Test
	fun testSuccessHandler02() {
		val rm = ResponseManager()

		val e = stanza("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0;

		rm.registerRequest(e) {
			onSuccess { _ -> ++successCounter }
			onError { _, _ -> fail() }
			onTimeout { fail() }
		}

		val resp = stanza("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		};
		val handler = rm.getHandler(resp)

		assertNotNull(handler)
		rm.run(handler!!, resp)
		assertEquals(1, successCounter)
	}

	@Test
	fun testSuccessHandler03() {
		val rm = ResponseManager()

		val e = stanza("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0;

		rm.registerRequest(e, onSuccess = { ++successCounter }, onError = { _, _ -> fail() }, onTimeout = { fail() })

		val resp = stanza("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		};
		val handler = rm.getHandler(resp)

		assertNotNull(handler)
		rm.run(handler!!, resp)
		assertEquals(1, successCounter)
	}

	@Test
	fun testError() {
		val rm = ResponseManager()

		val e = stanza("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var errorCounter = 0;

		rm.registerRequest(e).listen {
			onError { element, errorCondition ->
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
		};
		rm.getAndRun(resp)
		assertEquals(1, errorCounter)
	}

}