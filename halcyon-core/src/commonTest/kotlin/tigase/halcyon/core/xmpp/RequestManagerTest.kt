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

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.requests.IQRequest
import tigase.halcyon.core.requests.IQResponseHandler
import tigase.halcyon.core.requests.RequestsManager
import tigase.halcyon.core.requests.IQResult
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.MessageType
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
	fun testSuccessHandler01() {
		val rm = RequestsManager()

		val e = element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}

		var successCounter = 0

		val rq = halcyon.request.iq<Any>(e).response(object : IQResponseHandler<Any> {
			override fun success(request: IQRequest<Any>, responseStanza: IQ, v: Any?) {
				++successCounter
			}

			override fun error(
				request: IQRequest<Any>,
				responseStanza: IQ?,
				errorCondition: ErrorCondition,
				errorMessage: String?
			) {
				fail()
			}

		}).build()

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

		val req = halcyon.request.iq<Any>(e).handle {
			success { request, element, any -> ++successCounter }
			error { _, _, _, _ -> fail() }
		}.build();

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

		val req = halcyon.request.iq<Any>(e).response { result ->
			when (result) {
				is IQResult.Success ->{ ++successCounter}
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
	fun testErrorMessage() {
		val rm = RequestsManager()

		var handled = false

		val req = halcyon.request.message {
			to = "a@b.c".toJID()
			"body"{
				+"test"
			}
		}.error { messageRequest, element, errorCondition, errorMessage ->
			if (errorCondition == ErrorCondition.NotAllowed) handled = true
			else fail("Unexpected error type $errorCondition")
		}.build()

		rm.register(req)

		val res = element("message") {
			attribute("id", req.id)
			attribute("type", MessageType.Error.value)
			attribute("from", "a@b.c")
			element("error") {
				attribute("type", "cancel")
				element("not-allowed") {
					xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
				}
			}
		}
		rm.findAndExecute(res)
		assertTrue(handled)
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
		val req = halcyon.request.iq<Any>(e).handle {
			error { request, element, errorCondition, _ ->
				++errorCounter
				assertEquals(ErrorCondition.NotAllowed, errorCondition)
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

		val r1 = halcyon.request.iq<Any>(element("iq") {
			attribute("id", "1")
			attribute("type", "get")
			attribute("to", "a@b.c")
		}).timeToLive(0)
			.handle { error { _, _, errorCondition, _ -> if (errorCondition == ErrorCondition.RemoteServerTimeout) ++counter } }
			.build()
		rm.register(r1)

		val r2 = halcyon.request.iq<Any>(element("iq") {
			attribute("id", "2")
			attribute("type", "get")
			attribute("to", "a@b.c")
		})
			.handle { error { _, _, errorCondition, _ -> if (errorCondition == ErrorCondition.RemoteServerTimeout) ++counter } }
			.build()
		rm.register(r2)

		var r3 = halcyon.request.message { to = "a@b.c".toJID() }.timeToLive(0)
			.error { messageRequest, element, errorCondition, _ -> if (errorCondition == ErrorCondition.RemoteServerTimeout) ++counter }
			.build()
		rm.register(r3)

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