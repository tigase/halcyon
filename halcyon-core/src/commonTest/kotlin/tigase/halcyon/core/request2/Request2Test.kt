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
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Request2Test {

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
	fun testRequestMapSuccess() {
		var rr: Result<String>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.map { response -> response.getChildrenNS("response", "test")!!.value!! }.response { result ->
			if (result.isSuccess) rr = result
			val liczba = result.map { it.toLong() }
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
	fun testRequestWithNoMapSuccess() {
		var rr: Result<Unit>? = null

		val req = factory.iq {
			to = "a@b".toJID()
			from = "x@y".toJID()
			type = IQType.Get
		}.response { result -> if (result.isSuccess) rr = result }.build()

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
}