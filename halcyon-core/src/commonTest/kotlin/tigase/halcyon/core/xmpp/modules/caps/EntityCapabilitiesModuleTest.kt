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
import tigase.halcyon.core.xml.parser.parseXML
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
	fun test_more_complex_validate_verification_string() {
		val stanza = parseXML(
			"""
			<iq xmlns="jabber:client" from="sure.im" to="receiver@sure.im/1244996408-tigase-32233" id="ZZBfgtF7K7fR1bCbv5QtgfzD"
			    type="result">
			    <query xmlns="http://jabber.org/protocol/disco#info"
			           node="https://tigase.net/tigase-xmpp-server#pQoVbnROdqZOAkf9JCi34N8xkws=">
			        <identity name="Tigase ver. 8.4.0-SNAPSHOT-b12192/1431614e" category="component" type="router"/>
			        <identity name="Tigase ver. 8.4.0-SNAPSHOT-b12192/1431614e" category="server" type="im"/>
			        <identity category="pubsub" type="pep"/>
			        <feature var="http://jabber.org/protocol/commands"/>
			        <feature var="urn:xmpp:mix:pam:2"/>
			        <feature var="urn:xmpp:carbons:2"/>
			        <feature var="urn:xmpp:carbons:rules:0"/>
			        <feature var="vcard-temp"/>
			        <feature var="http://jabber.org/protocol/amp"/>
			        <feature var="msgoffline"/>
			        <feature var="jabber:iq:auth"/>
			        <feature var="http://jabber.org/protocol/disco#info"/>
			        <feature var="http://jabber.org/protocol/disco#items"/>
			        <feature var="urn:xmpp:blocking"/>
			        <feature var="urn:xmpp:reporting:0"/>
			        <feature var="urn:xmpp:reporting:abuse:0"/>
			        <feature var="urn:xmpp:reporting:spam:0"/>
			        <feature var="urn:xmpp:reporting:1"/>
			        <feature var="urn:xmpp:ping"/>
			        <feature var="urn:ietf:params:xml:ns:xmpp-sasl"/>
			        <feature var="http://jabber.org/protocol/pubsub"/>
			        <feature var="http://jabber.org/protocol/pubsub#owner"/>
			        <feature var="http://jabber.org/protocol/pubsub#publish"/>
			        <feature var="urn:xmpp:pep-vcard-conversion:0"/>
			        <feature var="urn:xmpp:bookmarks-conversion:0"/>
			        <feature var="urn:xmpp:archive:auto"/>
			        <feature var="urn:xmpp:archive:manage"/>
			        <feature var="urn:xmpp:push:0"/>
			        <feature var="tigase:push:away:0"/>
			        <feature var="tigase:push:encrypt:0"/>
			        <feature var="tigase:push:encrypt:aes-128-gcm"/>
			        <feature var="tigase:push:filter:ignore-unknown:0"/>
			        <feature var="tigase:push:filter:groupchat:0"/>
			        <feature var="tigase:push:filter:muted:0"/>
			        <feature var="urn:xmpp:mam:2"/>
			        <feature var="tigase:push:priority:0"/>
			        <feature var="tigase:push:jingle:0"/>
			        <feature var="jabber:iq:roster"/>
			        <feature var="jabber:iq:roster-dynamic"/>
			        <feature var="urn:xmpp:mam:1"/>
			        <feature var="urn:xmpp:mam:2#extended"/>
			        <feature var="urn:xmpp:mix:pam:2#archive"/>
			        <feature var="jabber:iq:version"/>
			        <feature var="urn:xmpp:time"/>
			        <feature var="jabber:iq:privacy"/>
			        <feature var="urn:ietf:params:xml:ns:xmpp-bind"/>
			        <feature var="urn:xmpp:extdisco:2"/>
			        <feature var="http://jabber.org/protocol/commands"/>
			        <feature var="urn:ietf:params:xml:ns:vcard-4.0"/>
			        <feature var="urn:ietf:params:xml:ns:xmpp-session"/>
			        <feature var="jabber:iq:private"/>
			        <x xmlns="jabber:x:data" type="result">
			            <field type="hidden" var="FORM_TYPE">
			                <value>http://jabber.org/network/serverinfo</value>
			            </field>
			            <field type="list-multi" var="abuse-addresses">
			                <value>mailto:support@tigase.net</value>
			                <value>xmpp:tigase@mix.tigase.im</value>
			                <value>xmpp:tigase@muc.tigase.org</value>
			                <value>https://tigase.net/technical-support</value>
			            </field>
			        </x>
			    </query>
			</iq>
		""".trimIndent()
		).element!!
		val info = halcyon.getModule(DiscoveryModule)
			.buildInfo(stanza)

		assertTrue("Verification string should be valid!") {
			halcyon.getModule(EntityCapabilitiesModule)
				.validateVerificationString(info)
		}
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