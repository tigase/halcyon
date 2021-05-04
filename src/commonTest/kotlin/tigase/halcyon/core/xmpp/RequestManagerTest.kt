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

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.requests.RequestsManager
import tigase.halcyon.core.requests.XMPPError
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.modules.vcard.VCardModule
import kotlin.test.*

class RequestManagerTest {

	val halcyon = object : AbstractHalcyon() {
		override fun reconnect(immediately: Boolean) {
			TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
		}

		override fun createConnector(): AbstractConnector {
			TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
		}
	}

	@Test
	fun testFindingResponseForRequestWithoutTo() {
		val rm = RequestsManager()
		rm.boundJID = "a@b.c/123".toJID()

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
		}
		val rq = halcyon.request.iq(e).build()
		rm.register(rq)

		val resp = element("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		}
		val handler = rm.getRequest(resp)

		assertNotNull(handler)
	}

	@Test
	fun testFindingResponseFromUnknownForRequestWithoutTo() {
		val rm = RequestsManager()
		rm.boundJID = "a@b.c/123".toJID()

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
		}

		val rq = halcyon.request.iq(e).build()
		rm.register(rq)

		val resp = element("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "mallet@badguys.org")
		}
		val handler = rm.getRequest(resp)

		assertNull(handler)
	}

	@Test
	fun testFindingResponseFromUnknownForRequest() {
		val rm = RequestsManager()
		rm.boundJID = "a@b.c/123".toJID()

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		val rq = halcyon.request.iq(e).build()
		rm.register(rq)

		val resp = element("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "mallet@badguys.org")
		}
		val handler = rm.getRequest(resp)

		assertNull(handler)
	}

	@Test
	fun testFindingResponseWithoutFromForRequestWithoutTo() {
		val rm = RequestsManager()
		rm.boundJID = "a@b.c/123".toJID()

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
		}

		val rq = halcyon.request.iq(e).build()
		rm.register(rq)

		val resp = element("iq") {
			attribute("id", "1")
			attribute("type", "result")
		}
		val handler = rm.getRequest(resp)

		assertNotNull(handler)
	}

	@Test
	fun testSuccessHandler01() {
		val rm = RequestsManager()

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0

		val rq = halcyon.request.iq(e).response {
			if (it.isSuccess) {
				++successCounter
			} else {
				fail()
			}
		}.build()

		rm.register(rq)

		val resp = element("iq") {
			attribute("id", "1")
			attribute("type", "result")
			attribute("from", "a@b.c")
		}
		val handler = rm.getRequest(resp)

		assertNotNull(handler)
		handler.setResponseStanza(resp)
		assertEquals(1, successCounter)
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

		val req = halcyon.request.iq(e).response {
			if (it.isSuccess) ++successCounter
		}.build()

		rm.register(req)

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

		val req = halcyon.request.iq(e).response { result ->
			when {
				result.isSuccess -> {
					++successCounter
				}
				else -> fail()
			}
		}.build()

		rm.register(req)

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
	fun testErrorIQ() {
		val rm = RequestsManager()
		var errorCounter = 0

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}
		val req = halcyon.request.iq(e).response {
			if (it.isFailure) {
				++errorCounter
				assertEquals(ErrorCondition.NotAllowed, (it.exceptionOrNull()!! as XMPPError).error)
			}
		}.build()


		rm.register(req)

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

		// timout expected
		val r1 = halcyon.request.iq(element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}).timeToLive(0).response {
			it.onFailure {
				if ((it as XMPPError).error == ErrorCondition.RemoteServerTimeout) ++counter
			}
		}.build()
		rm.register(r1)

		// timout NOT expected
		val r2 = halcyon.request.iq(element("iq") {
			attribute("id", "2")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}).response {
			it.onFailure {
				if ((it as XMPPError).error == ErrorCondition.RemoteServerTimeout) ++counter

			}
		}.build()
		rm.register(r2)


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

	@Test
	fun testExceptionOnMapping() {
		var catchedException: Throwable? = null
		val req = halcyon.request.iq {
			attribute("id", "1")
			attribute("type", "get")
			attribute("from", "my@jid.com")
			attribute("to", "to@jid.com")
		}.map {
			throw XMPPError(null, ErrorCondition.Gone, "TeST")
		}.response {
			it.onSuccess {
				fail("it shouldn't be called at all")
			}
			it.onFailure {
				catchedException = it
			}
		}.build()
		halcyon.requestsManager.register(req)
		val respStanza = element("iq") {
			xmlns = "jabber:client"
			attribute("to", "my@jid.com")
			attribute("from", "to@jid.com")
			attribute("type", "result")
			attribute("id", req.id)
		}
		val result = halcyon.requestsManager.findAndExecute(respStanza)
		assertTrue(result)
		assertNotNull(catchedException).let {
			assertTrue(it is XMPPError)
			assertEquals(ErrorCondition.Gone, it.error)
			assertEquals("TeST", it.description)
		}
	}

	@Test
	fun testErrorProcessing() {
		val module = halcyon.getModule<VCardModule>(VCardModule.TYPE)

		var catchedException: Throwable? = null

		val req = module.retrieveVCard("to@jid.com".toBareJID()).response {
			it.onSuccess {
				fail("it shouldn't be called at all")
			}
			it.onFailure {
				catchedException = it
			}
		}.build()
		halcyon.requestsManager.register(req)

		val respStanza = element("iq") {
			xmlns = "jabber:client"
			attribute("to", "my@jid.com")
			attribute("from", "to@jid.com")
			attribute("type", "error")
			attribute("id", req.id)
			"error"{
				attribute("type", "cancel")
				"service-unavailable"{
					xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
				}
			}
		}

		val result = halcyon.requestsManager.findAndExecute(respStanza)
		assertTrue(result)

		assertNotNull(catchedException).let {
			assertTrue(it is XMPPError)
			assertEquals(ErrorCondition.ServiceUnavailable, it.error)
		}

	}

}