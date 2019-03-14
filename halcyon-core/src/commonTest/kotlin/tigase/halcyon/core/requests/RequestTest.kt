package tigase.halcyon.core.requests

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.JID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestTest {

	@Test
	fun testLateCallbackInit() {
		val req = Request(JID.parse("a@b.c"), "1", 1, element("iq") {
			attribute("id", "123")
			attribute("to", "a@b.c")
			attribute("type", "set")
		})

		val response = element("iq") {
			attribute("id", "123")
			attribute("from", "a@b.c")
			attribute("type", "result")
		}

		req.responseStanza = response

		var rr: Request.Result? = null
		req.response { request, element, result -> rr = result }

		assertTrue(rr is Request.Result.Success)
	}

	@Test
	fun testEarlyCallbackInit() {
		val req = Request(JID.parse("a@b.c"), "1", 1, element("iq") {
			attribute("id", "123")
			attribute("to", "a@b.c")
			attribute("type", "set")
		})

		val response = element("iq") {
			attribute("id", "123")
			attribute("from", "a@b.c")
			attribute("type", "result")
		}
		var rr: Request.Result? = null
		req.response { request, element, result -> rr = result }

		req.responseStanza = response

		assertNotNull(rr, "Result cannot be null here!")
		assertTrue(rr is Request.Result.Success, "Result isn't Success")
	}

	@Test
	fun testResponseSuccess() {
		val req = Request(JID.parse("a@b.c"), "1", 1, element("iq") {
			attribute("id", "123")
			attribute("to", "a@b.c")
			attribute("type", "set")
		})

		val response = element("iq") {
			attribute("id", "123")
			attribute("from", "a@b.c")
			attribute("type", "result")
		}

		var rr: Element? = null
		req.handle {
			success { request, element ->
				rr = element
			}
		}

		req.responseStanza = response

		assertEquals(response, rr)
	}

	@Test
	fun testResponseError() {
		val req = Request(JID.parse("a@b.c"), "1", 1, element("iq") {
			attribute("id", "123")
			attribute("to", "a@b.c")
			attribute("type", "set")
		})

		val response = element("iq") {
			attribute("id", "123")
			attribute("from", "a@b.c")
			attribute("type", "error")
		}

		var rr: Request.Result? = null
		req.response { request, element, result -> rr = result }

		req.responseStanza = response

		assertNotNull(rr, "Result cannot be null here!")
		assertTrue(rr is Request.Result.Error, "Result isn't Error")
	}

}