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
package tigase.halcyon.core.xmpp.modules.pubsub

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.stanzas.message
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PubSubTest {

	@Test
	fun testProcessAndEvents() {
		val halcyon = object : AbstractHalcyon() {
			override fun reconnect(immediately: Boolean) = TODO("not implemented")
			override fun createConnector(): AbstractConnector = TODO("not implemented")
		}
		val pubsub = assertNotNull(halcyon.getModule<PubSubModule>(PubSubModule.TYPE))

		val published = mutableMapOf<String, Any>()
		val retracted = mutableSetOf<String>()

		halcyon.eventBus.register<PubSubItemEvent>(PubSubItemEvent.TYPE) {
			when (it) {
				is PubSubItemEvent.Published -> published.put(it.itemId!!, it.content ?: true)
				is PubSubItemEvent.Retracted -> retracted.add(it.itemId!!)
			}
		}

		pubsub.process(message {
			from = "pubsub.shakespeare.lit".toJID()
			to = "francisco@denmark.lit".toJID()
			"event"{
				xmlns = "http://jabber.org/protocol/pubsub#event"
				"items"{
					attribute("node", "princely_musings")
					"item"{
						attribute("id", "item-1")
						"data"{
							+"test"
						}
					}
					"item"{
						attribute("id", "item-2")
					}
				}
			}
		})
		assertEquals(2, published.size)
		assertEquals(0, retracted.size)

		assertEquals(element("data") { +"test" }, published["item-1"]!!)
		assertEquals(true, published["item-2"]!!)

		pubsub.process(message {
			from = "pubsub.shakespeare.lit".toJID()
			to = "bernardo@denmark.lit".toJID()
			"event"{
				xmlns = "http://jabber.org/protocol/pubsub#event"
				"items"{
					attribute("node", "princely_musings")
					"retract"{
						attribute("id", "item-3")
					}
				}
			}
		})
		assertEquals(2, published.size)
		assertEquals(1, retracted.size)

		assertTrue(retracted.contains("item-3"))

	}

}