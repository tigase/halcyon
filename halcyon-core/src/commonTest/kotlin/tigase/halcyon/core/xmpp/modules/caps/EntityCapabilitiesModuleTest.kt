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
package tigase.halcyon.core.xmpp.modules.caps

import tigase.DummyHalcyon
import tigase.halcyon.core.xmpp.modules.StreamFeaturesModule
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityCapabilitiesModuleTest {

	val halcyon = DummyHalcyon().apply {
		connect()
	}

	@ExperimentalStdlibApi
	@Test
	fun testCalculateVerificationString() {
		val module = EntityCapabilitiesModule(halcyon, DiscoveryModule(halcyon), StreamFeaturesModule(halcyon))

		val identities = listOf(DiscoveryModule.Identity("client", "pc", "Exodus 0.9.1"))
		val features = listOf(
			"http://jabber.org/protocol/disco#items",
			"http://jabber.org/protocol/muc",
			"http://jabber.org/protocol/caps",
			"http://jabber.org/protocol/disco#info"
		)

		assertEquals("QgayPKawpkPSDYmwT/WM94uAlu0=", module.calculateVer(identities, features))
	}

}