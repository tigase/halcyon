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
package tigase.halcyon.core.request2

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.requests.RequestBuilderFactory
import tigase.halcyon.core.requests.XMPPError
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.*

class RequestsTest {

	private val halcyon = object : AbstractHalcyon() {
		override fun reconnect(immediately: Boolean) {
			TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
		}

		override fun createConnector(): AbstractConnector {
			TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
		}
	}

	private val factory = RequestBuilderFactory(halcyon)

	@Test
	fun testMap2Stacking() {
		var rr1: Result<Long>? = null
		var rr12: Result<Long>? = null
		var rr2: Result<Long>? = null

		var respCounter1 = 0
		var respCounter2 = 0
		var mapCounter1 = 0
		var mapCounter2 = 0
		var mapCounter3 = 0
		var mapCounter4 = 0

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.map { value ->
			++mapCounter1
			value.getChildrenNS("response", "test")!!.value!!
		}.map { value ->
			++mapCounter2
			value.toLong()
		}.response { result ->
			++respCounter1
			rr1 = result
		}.response { result ->
			++respCounter1
			rr12 = result
		}.map { value ->
			++mapCounter3
			value + 1
		}.map { value ->
			++mapCounter4
			value + 1
		}.response { result ->
			++respCounter2
			rr2 = result
		}.build()

		val response = iq {
			from = "a@b".toJID()
			to = "x@y".toJID()
			type = IQType.Result
			"response"{
				xmlns = "test"
				+"1234"
			}
			"response2"{
				xmlns = "test"
				+"9832"
			}
		}
		req.setResponseStanza(response)

		assertNotNull(rr1).let {
			assertTrue(it.isSuccess)
			assertEquals(1234, it.getOrNull())
		}
		assertNotNull(rr12).let {
			assertTrue(it.isSuccess)
			assertEquals(1234, it.getOrNull())
		}

		assertNotNull(rr2).let {
			assertTrue(it.isSuccess)
			assertEquals(1236, it.getOrNull())
		}

		assertEquals(2, respCounter1, "Response handler must be called twice!")
		assertEquals(1, respCounter2, "Response handler must be called once!")
		assertEquals(1, mapCounter1, "Mapping must be executed once!")
		assertEquals(1, mapCounter2, "Mapping must be executed once!")
		assertEquals(1, mapCounter3, "Mapping must be executed once!")
		assertEquals(1, mapCounter4, "Mapping must be executed once!")

	}

	@Test
	fun testRequestMapSuccess() {
		var rr: Result<String>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.map { response -> response.getChildrenNS("response", "test")!!.value!! }.response { result ->
			if (result.isSuccess) rr = result
		}.build()

		val response = iq {
			from = "a@b".toJID()
			to = "x@y".toJID()
			type = IQType.Result
			"response"{
				xmlns = "test"
				+"1234"
			}
		}
		req.setResponseStanza(response)

		val rrNotNull = assertNotNull(rr)
		assertTrue(rrNotNull.isSuccess)
		assertEquals("1234", rrNotNull.getOrNull())
	}

	@Test
	fun testResponseStanzaHandler() {
		var rr: Result<String>? = null
		var rs: IQ? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.map { response -> response.getChildrenNS("response", "test")!!.value!! }.response { result ->
			if (result.isSuccess) rr = result
		}.handleResponseStanza { request, iq ->
			rs = iq
		}.build()

		val response = iq {
			from = "a@b".toJID()
			to = "x@y".toJID()
			type = IQType.Result
			"response"{
				xmlns = "test"
				+"1234"
			}
		}
		req.setResponseStanza(response)

		assertEquals(response, assertNotNull(rs))

		val rrNotNull = assertNotNull(rr)
		assertTrue(rrNotNull.isSuccess)
		assertEquals("1234", rrNotNull.getOrNull())
	}

	@Test
	fun testRequestWithNoMapSuccess() {
		var rr: Result<IQ>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.response { rr = it }.build()

		val response = iq {
			from = "a@b".toJID()
			to = "x@y".toJID()
			type = IQType.Result
			"response"{
				xmlns = "test"
				+"1234"
			}
		}
		req.setResponseStanza(response)

		val rrNotNull = assertNotNull(rr)
		assertTrue(rrNotNull.isSuccess)
	}

	@Test
	fun testRequestManyHandlersSuccess() {
		var rr1: Result<IQ>? = null
		var rr2: Result<IQ>? = null
		var rr3: Result<IQ>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.response { rr1 = it }.response { rr2 = it }.response { rr3 = it }.build()

		val response = iq {
			from = "a@b".toJID()
			to = "x@y".toJID()
			type = IQType.Result
			"response"{
				xmlns = "test"
				+"1234"
			}
		}
		req.setResponseStanza(response)

		assertNotNull(rr1).let {
			assertTrue(it.isSuccess)
		}
		assertNotNull(rr2).let {
			assertTrue(it.isSuccess)
		}
		assertNotNull(rr3).let {
			assertTrue(it.isSuccess)
		}
	}

	@Test
	fun testRequestManyHandlersError() {
		var rr1: Result<IQ>? = null
		var rr2: Result<IQ>? = null
		var rr3: Result<IQ>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.response { rr1 = it }.response { rr2 = it }.response { rr3 = it }.build()

		val response = iq {
			from = "a@b".toJID()
			to = "x@y".toJID()
			type = IQType.Error
		}
		req.setResponseStanza(response)

		assertNotNull(rr1).let {
			assertTrue(it.isFailure)
		}
		assertNotNull(rr2).let {
			assertTrue(it.isFailure)
		}
		assertNotNull(rr3).let {
			assertTrue(it.isFailure)
		}
	}

	@Test
	fun testResponseTimeout() {
		var rr: Result<*>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.response {
			rr = it
		}.build()
		req.markTimeout()

		val exNotNull = assertNotNull(assertNotNull(rr).exceptionOrNull())
		assertTrue(exNotNull is XMPPError)
		assertEquals(ErrorCondition.RemoteServerTimeout, exNotNull.error)
	}

	@Test
	fun testResponseTimeoutStacked() {
		var rr1: Result<*>? = null
		var rr2: Result<*>? = null

		var respCounter1 = 0
		var respCounter2 = 0
		var mapCounter1 = 0

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.response {
			++respCounter1
			rr1 = it
		}.map {
			++mapCounter1
			it.getChildrenNS("response", "test")!!.value!!
		}.response {
			++respCounter2
			rr2 = it
		}.build()
		req.markTimeout()

		val exNotNull = assertNotNull(assertNotNull(rr1).exceptionOrNull())
		assertTrue(exNotNull is XMPPError)
		assertEquals(ErrorCondition.RemoteServerTimeout, exNotNull.error)
		val exNotNull2 = assertNotNull(assertNotNull(rr2).exceptionOrNull())
		assertTrue(exNotNull2 is XMPPError)
		assertEquals(ErrorCondition.RemoteServerTimeout, exNotNull2.error)

		assertEquals(1, respCounter1, "Response handler must be called once!")
		assertEquals(1, respCounter2, "Response handler must be called once!")
		assertEquals(0, mapCounter1, "Mapping cannot be called")
	}

	@Test
	fun testResponseError() {
		var rr: Result<*>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.response {
			rr = it
		}.build()

		val response = iq {
			from = "a@b".toJID()
			to = "x@y".toJID()
			type = IQType.Error
			"error"{
				attributes["code"] = "404"
				attributes["type"] = "cancel"
				"item-not-found"{ xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas" }
				"text"{
					xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
					+"Test message"
				}
			}
		}
		req.setResponseStanza(response)

		val rrNotNull = assertNotNull(rr, "Result cannot be null here!")
		assertTrue(rrNotNull.isFailure, "Result isn't Error")

		val exNotNull = assertNotNull(rrNotNull.exceptionOrNull())
		assertTrue(exNotNull is XMPPError)

		assertEquals(ErrorCondition.ItemNotFound, exNotNull.error)
		assertEquals("Test message", exNotNull.description)
	}

	@Test
	fun testResponseErrorStacked() {
		var rr: Result<String>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.map { it }.map { it.name }.response {
			rr = it
		}.build()

		val response = iq {
			from = "a@b".toJID()
			to = "x@y".toJID()
			type = IQType.Error
			"error"{
				attributes["code"] = "404"
				attributes["type"] = "cancel"
				"item-not-found"{ xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas" }
				"text"{
					xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
					+"Test message"
				}
			}
		}
		req.setResponseStanza(response)

		val rrNotNull = assertNotNull(rr, "Result cannot be null here!")
		assertTrue(rrNotNull.isFailure, "Result isn't Error")

		val exNotNull = assertNotNull(rrNotNull.exceptionOrNull())
		assertTrue(exNotNull is XMPPError)

		assertEquals(ErrorCondition.ItemNotFound, exNotNull.error)
		assertEquals("Test message", exNotNull.description)
	}

	@Test
	fun testMarkAsSentIQ() {
		var rr: Result<*>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.response {
			rr = it
		}.build()
		req.markAsSent()

		assertNull(rr, "Handler must not be executed for IQ stanza, if markAsRead() is called.")
		assertTrue(req.isSent)
	}

	@Test
	fun testMarkAsSentIQStacked() {
		var rr: Result<*>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.map { Unit }.response {
			rr = it
		}.map { Unit }.build()
		req.markAsSent()

		assertNull(rr, "Handler must not be executed for IQ stanza, if markAsRead() is called.")
		assertTrue(req.isSent)
	}

	@Test
	fun testMarkAsSentMessage() {
		var rr: Result<*>? = null

		val req = factory.message {
			to = "a@b".toJID()
			from = "x@y".toJID()
		}.response {
			rr = it
		}.build()
		req.markAsSent()

		val rrNN = assertNotNull(rr, "We should have any response here!")
		assertTrue(rrNN.isSuccess, "Result should be success, because stanza is sent")
		assertTrue(req.isSent)
	}

	@Test
	fun testMarkAsSentMessageStacked() {
		var rr: Result<*>? = null
		var rr1: Result<String>? = null

		var respCounter1 = 0
		var respCounter2 = 0
		var mapCounter1 = 0
		var mapCounter2 = 0

		val req = factory.message {
			to = "a@b".toJID()
			from = "x@y".toJID()
		}.map {
			++mapCounter1
			Unit
		}.response {
			++respCounter1
			rr = it
		}.map {
			++mapCounter2
			"Sent"
		}.response {
			++respCounter2
			rr1 = it
		}.build()
		req.markAsSent()

		assertTrue(req.isSent)

		val rrNN = assertNotNull(rr, "We should have any response here!")
		assertTrue(rrNN.isSuccess, "Result should be success, because stanza is sent")

		val rr1NN = assertNotNull(rr1, "We should have any response here!")
		assertTrue(rr1NN.isSuccess, "Result should be success, because stanza is sent")
		assertEquals("Sent", rr1NN.getOrNull())

		assertEquals(1, respCounter1, "Response handler must be called once!")
		assertEquals(1, respCounter2, "Response handler must be called once!")
		assertEquals(1, mapCounter1, "Mapping must be executed once!")
		assertEquals(1, mapCounter2, "Mapping must be executed once!")
	}
}