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
package tigase.halcyon.core.xmpp.modules.vcard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VCardBuilderTest {

	@Test
	fun testSimpleBuild() {
		val c = vcard {
			nickname = "ala"
		}
		assertEquals(
			"<vcard xmlns=\"urn:ietf:params:xml:ns:vcard-4.0\"><nickname><text>ala</text></nickname></vcard>",
			c.element.getAsString()
		)
		assertEquals(1, c.element.children.size)
		assertEquals("nickname", c.element.children[0].name)
		assertEquals("ala", c.element.findChild("vcard", "nickname", "text")?.value)
	}

	@Test
	fun testVCardEmptyDataBuild() {
		val c = vcard {
			structuredName {
				given = ""
				surname = ""
				additional = ""
			}
			nickname = "Alice"
			birthday = ""
			org {
				name = ""
			}
			telephone { }
			telephone {
				uri = ""
				parameters { }
			}
			address {
				code = ""
				ext = ""
			}
		}

		assertEquals(1, c.element.children.size)
		assertEquals("nickname", c.element.children[0].name)
	}

	@Test
	fun testVCardBuild() {
		val c = vcard {
			structuredName {
				given = "Alice"
				surname = "Carl"
				additional = "Von"
			}
			nickname = "Alice"
			birthday = "Friday"
			formattedName = "Alice Von Carl"
			role = "Senior Coffee Drinker"
			timeZone = "GMT"
			address {
				parameters {
					pref = 132
					+"home"
				}
				street = "Sunny"
				ext = "Room 2"
				locality = "Fresh Air"
				region = "Good Looking"
				code = "1234"
				country = "ANYWHERE"
			}
			email {
				parameters {
					pref = 42
					+"wrk"
				}
				text = "a1231@b.c"
			}
			org {
				parameters {
					+"work"
				}
				name = "ACME"
			}
			telephone {
				uri = "tel:123"
				parameters {
					pref = 42
					+"work"
				}
			}
			telephone {
				uri = "tel:999"
				parameters {
					pref = 1
					+"home"
				}
			}
			photoUri {
				uri = "http://123"
			}
			photoData {
				imageType = "image/png"
				data = "iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAAMI2lDQ1BJQ0MgUHJvZmlsZQAASImVVwdUk8kWnr+kktACoUg"
			}
		}

		assertEquals("Alice", c.element.findChild("vcard", "nickname", "text")?.value)
		assertEquals("Alice", c.nickname)
		assertEquals("Friday", c.element.findChild("vcard", "bday", "date")?.value)
		assertEquals("Friday", c.birthday)
		assertEquals("Alice Von Carl", c.element.findChild("vcard", "fn", "text")?.value)
		assertEquals("Alice Von Carl", c.formattedName)
		assertEquals("Senior Coffee Drinker", c.element.findChild("vcard", "role", "text")?.value)
		assertEquals("Senior Coffee Drinker", c.role)
		assertEquals("GMT", c.element.findChild("vcard", "tz", "text")?.value)
		assertEquals("GMT", c.timeZone)

		assertEquals("Room 2", c.element.findChild("vcard", "adr", "ext")?.value)
		assertEquals("Sunny", c.element.findChild("vcard", "adr", "street")?.value)
		assertEquals("Fresh Air", c.element.findChild("vcard", "adr", "locality")?.value)
		assertEquals("Good Looking", c.element.findChild("vcard", "adr", "region")?.value)
		assertEquals("1234", c.element.findChild("vcard", "adr", "code")?.value)
		assertEquals("ANYWHERE", c.element.findChild("vcard", "adr", "country")?.value)
		assertEquals("132", c.element.findChild("vcard", "adr", "parameters", "pref", "integer")?.value)
		assertEquals("home", c.element.findChild("vcard", "adr", "parameters", "type", "text")?.value)
		assertEquals(1, c.addresses.size)
		c.addresses.first().let { adr ->
			assertEquals("Room 2", adr.ext)
			assertEquals("Sunny", adr.street)
			assertEquals("Fresh Air", adr.locality)
			assertEquals("Good Looking", adr.region)
			assertEquals("1234", adr.code)
			assertEquals(132, adr.parameters.pref)
			assertTrue(adr.parameters.types.contains("home"))
		}
		assertEquals(1, c.emails.size)
		c.emails.first().let { e ->
			assertEquals("a1231@b.c", e.text)
			assertEquals(42, e.parameters.pref)
			assertTrue(e.parameters.types.contains("wrk"))
		}
		assertEquals("ACME", c.element.findChild("vcard", "org", "text")?.value)
		assertEquals("work", c.element.findChild("vcard", "org", "parameters", "type", "text")?.value)
		assertEquals(1, c.organizations.size)
		c.organizations.first().let {
			assertTrue(it.parameters.types.contains("work"))
			assertEquals("ACME", it.name)
		}
		assertEquals("Alice", c.element.findChild("vcard", "n", "given")?.value)
		assertEquals("Carl", c.element.findChild("vcard", "n", "surname")?.value)
		assertEquals("Von", c.element.findChild("vcard", "n", "additional")?.value)
		c.structuredName.let {
			assertEquals("Alice", it!!.given)
			assertEquals("Carl", it.surname)
			assertEquals("Von", it.additional)
		}
		assertEquals("tel:123", c.element.findChild("vcard", "tel", "uri")?.value)
		assertEquals("work", c.element.findChild("vcard", "org", "parameters", "type", "text")?.value)
		assertEquals(2, c.telephones.size)
		c.telephones.first().let {
			assertTrue(it.parameters.types.contains("work"))
			assertEquals("tel:123", it.uri)
		}
		val photos = c.element.getChildren("photo")
		assertEquals(2, photos.size)

		assertEquals("http://123", photos[0].findChild("photo", "uri")?.value)
		assertEquals(
			"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAAMI2lDQ1BJQ0MgUHJvZmlsZQAASImVVwdUk8kWnr+kktACoUg",
			photos[1].findChild("photo", "uri")?.value
		)
	}

}