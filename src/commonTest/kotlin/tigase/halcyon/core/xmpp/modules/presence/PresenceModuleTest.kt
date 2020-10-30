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
package tigase.halcyon.core.xmpp.modules.presence

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.xmpp.stanzas.Presence
import tigase.halcyon.core.xmpp.stanzas.PresenceType
import tigase.halcyon.core.xmpp.stanzas.Show
import tigase.halcyon.core.xmpp.stanzas.presence
import tigase.halcyon.core.xmpp.toBareJID
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PresenceModuleTest {

	val halcyon = object : AbstractHalcyon() {
		override fun reconnect(immediately: Boolean) = TODO("not implemented")
		override fun createConnector(): AbstractConnector = TODO("not implemented")
	}

	@Test
	fun testGetAllResources() {
		val module = PresenceModule(halcyon)
		module.process(presence {
			from = "a@b.c/1".toJID()
			show = Show.Away
		})
		module.process(presence {
			from = "a@b.c/2".toJID()
			show = Show.XA
		})
		module.process(presence {
			from = "a@b.c/3".toJID()
			show = Show.DnD
			priority = 100
		})
		module.process(presence {
			from = "a@b.c/3".toJID()
			show = Show.DnD
			priority = -100
		})

		val list = module.getResources("a@b.c".toBareJID())
		assertEquals(3, list.size)

	}

	@Test
	fun testGetPresenceOf() {
		val module = PresenceModule(halcyon)

		module.process(presence {
			id()
			from = "a@b.c/1".toJID()
		})
		module.process(presence {
			from = "a@b.c/2".toJID()
		})
		assertEquals(2, module.getResources("a@b.c".toBareJID()).size)
		assertEquals(0, module.getResources("_a@b.c".toBareJID()).size)

		assertNotNull(module.getPresenceOf("a@b.c/1".toJID()))
		assertNotNull(module.getPresenceOf("a@b.c/2".toJID()))
		assertNull(module.getPresenceOf("a@b.c/3".toJID()))

		module.process(presence {
			from = "a@b.c/2".toJID()
			type = PresenceType.Unavailable
		})

		assertEquals(1, module.getResources("a@b.c".toBareJID()).size)
		assertNull(module.getPresenceOf("a@b.c/2".toJID()))

	}

	@Test
	fun testGetPresenceOfBareJid() {
		val module = PresenceModule(halcyon)

		module.process(presence {
			from = "a@b.c/1".toJID()
			show = Show.Away
		})
		module.process(presence {
			from = "a@b.c/2".toJID()
			show = Show.XA
		})

		assertEquals(Show.Away, module.getBestPresenceOf("a@b.c".toBareJID())!!.show)

		module.process(presence {
			from = "a@b.c/3".toJID()
			show = Show.DnD
			priority = 100
		})

		assertEquals(Show.DnD, module.getBestPresenceOf("a@b.c".toBareJID())!!.show)

		module.process(presence {
			from = "a@b.c/3".toJID()
			show = Show.DnD
			priority = -100
		})

		assertEquals(Show.Away, module.getBestPresenceOf("a@b.c".toBareJID())!!.show)

		module.process(presence {
			from = "a@b.c/1".toJID()
			type = PresenceType.Unavailable
			show = Show.DnD
		})
		assertEquals(Show.XA, module.getBestPresenceOf("a@b.c".toBareJID())!!.show)
	}

	@Test
	fun testTypeAndShow() {
		assertEquals(TypeAndShow.Error, presence {
			type = PresenceType.Error
			show = Show.Chat
		}.typeAndShow())

		assertEquals(TypeAndShow.Chat, presence {
			show = Show.Chat
		}.typeAndShow())

		assertEquals(TypeAndShow.Unknown, presence {
			type = PresenceType.Subscribed
		}.typeAndShow())

		assertEquals(TypeAndShow.Offline, presence {
			type = PresenceType.Unavailable
		}.typeAndShow())

		assertEquals(TypeAndShow.Online, presence { }.typeAndShow())

		val p: Presence? = null
		assertEquals(TypeAndShow.Offline, p.typeAndShow())
	}

}