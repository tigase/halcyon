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
import tigase.halcyon.core.xmpp.forms.Field
import tigase.halcyon.core.xmpp.forms.FieldType
import tigase.halcyon.core.xmpp.forms.FormType
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.modules.StreamFeaturesModule
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityCapabilitiesModuleTest {

	val halcyon = DummyHalcyon().apply {
		connect()
	}

	@Test
	fun test_validateVerificationString() {
		val info = halcyon.getModule(DiscoveryModule)
			.buildInfo(iq {
				type = IQType.Result
				to = "juliet@capulet.lit/chamber".toJID()
				from = "benvolio@capulet.lit/230193".toJID()
				query("http://jabber.org/protocol/disco#info") {
					attribute("node", "http://psi-im.org#q07IKJEyjvHSyhy//CH0CxmKi8w=")
					"identity" {
						attributes["xml:lang"] = "en"
						attributes["name"] = "Psi 0.11"
						attributes["category"] = "client"
						attributes["type"] = "pc"
					}
					"identity" {
						attributes["xml:lang"] = "el"
						attributes["name"] = "Ψ 0.11"
						attributes["category"] = "client"
						attributes["type"] = "pc"
					}
					"feature" { attributes["var"] = "http://jabber.org/protocol/caps" }
					"feature" { attributes["var"] = "http://jabber.org/protocol/disco#info" }
					"feature" { attributes["var"] = "http://jabber.org/protocol/disco#items" }
					"feature" { attributes["var"] = "http://jabber.org/protocol/muc" }
					"x" {
						xmlns = "jabber:x:data"
						attribute("type", "result")
						"field" {
							attribute("type", "hidden")
							attribute("var", "FORM_TYPE")
							"value" { +"urn:xmpp:dataforms:softwareinfo" }
						}
						"field" {
							attribute("type", "text-multi")
							attribute("var", "ip_version")
							"value" { +"ipv4" }
							"value" { +"ipv6" }
						}
						"field" {
							attribute("var", "os")
							"value" { +"Mac" }
						}
						"field" {
							attribute("var", "os_version")
							"value" { +"10.5.1" }
						}
						"field" {
							attribute("var", "software")
							"value" { +"Psi" }
						}
						"field" {
							attribute("var", "software_version")
							"value" { +"0.11" }
						}
					}
				}
			})
		assertTrue("Verification string should be valid!") {
			halcyon.getModule(EntityCapabilitiesModule)
				.validateVerificationString(info)
		}
	}

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

	@Test
	fun testComplexGenerationVerificationString() {
		val module = EntityCapabilitiesModule(halcyon, DiscoveryModule(halcyon), StreamFeaturesModule(halcyon))

		val identities = listOf(
			DiscoveryModule.Identity("client", "pc", "Psi 0.11", "en"),
			DiscoveryModule.Identity("client", "pc", "Ψ 0.11", "el")
		)
		val features = listOf(
			"http://jabber.org/protocol/caps",
			"http://jabber.org/protocol/disco#info",
			"http://jabber.org/protocol/disco#items",
			"http://jabber.org/protocol/muc"
		)

		val form = JabberDataForm.create(FormType.Result)
			.apply {
				addField("FORM_TYPE", FieldType.Hidden).fieldValue = "urn:xmpp:dataforms:softwareinfo"
				addField("ip_version", FieldType.TextMulti).fieldValues = listOf("ipv4", "ipv6")
				addField("os", null).fieldValue = "Mac"
				addField("os_version", null).fieldValue = "10.5.1"
				addField("software", null).fieldValue = "Psi"
				addField("software_version", null).fieldValue = "0.11"

			}

		assertEquals("q07IKJEyjvHSyhy//CH0CxmKi8w=", module.calculateVer(identities, features, listOf(form)))
	}

}