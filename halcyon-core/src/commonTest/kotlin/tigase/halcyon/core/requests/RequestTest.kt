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
package tigase.halcyon.core.requests

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestTest {
	val halcyon = object : AbstractHalcyon() {
		override fun reconnect(immediately: Boolean) {
			TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
		}

		override fun createConnector(): AbstractConnector {
			TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
		}
	}

	@Test
	fun testEarlyCallbackInit() {
		var rr: IQResult<Any>? = null

		val req = halcyon.request.iq<Any>(iq {
			type = IQType.Set
			to = JID.parse("a@b.c")
		}).response { result -> rr = result }.build()

		val response = element("iq") {
			attribute("id", req.id)
			attribute("from", "a@b.c")
			attribute("type", "result")
		}
		req.setResponseStanza(response)

		assertNotNull(rr, "Result cannot be null here!")
		assertTrue(rr is IQResult.Success, "Result isn't Success")
	}

	@Test
	fun testResponseSuccess() {
		var rr: Element? = null

		val req = halcyon.request.iq<Any>(iq {
			type = IQType.Set
			to = "a@b.c".toJID()
		}).handle {
			success { _, element, _ ->
				rr = element
			}
		}.build()

		val response = element("iq") {
			attribute("id", req.id)
			attribute("from", "a@b.c")
			attribute("type", "result")
		}

		req.setResponseStanza(response)

		assertEquals(response, rr)
		assertEquals(response.getAsString(), rr!!.getAsString())
	}

	@Test
	fun testResponseError() {
		var rr: IQResult<Any>? = null
		val req = halcyon.request.iq<Any>(iq {
			type = IQType.Set
			to = "a@b.c".toJID()
		}).response { result -> rr = result }.build()

		val response = element("iq") {
			attribute("id", req.id)
			attribute("from", "a@b.c")
			attribute("type", "error")
		}

		req.setResponseStanza(response)

		assertNotNull(rr, "Result cannot be null here!")
		assertTrue(rr is IQResult.Error, "Result isn't Error")
	}

}