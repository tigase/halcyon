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

import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xml.parser.parseXML
import kotlin.test.*

class VCardTest {

	val sample = """
		<vcard xmlns="urn:ietf:params:xml:ns:vcard-4.0">
    <fn>
        <text>Samantha Mizzi</text>
    </fn>
    <n>
        <surname>Mizzi</surname>
        <given>Samantha</given>
        <additional></additional>
    </n>
    <nickname>
        <text>Sam</text>
    </nickname>
    <nickname>
        <text>samizzi</text>
    </nickname>
    <geo>
        <uri>geo:39.59,-105.01</uri>
    </geo>
    <org>
        <parameters>
            <type>
                <text>work</text>
            </type>
        </parameters>
        <text>Cisco</text>
    </org>
    <org>
        <parameters>
            <pref>
                <integer>2</integer>
            </pref>
            <type>
                <text>work</text>
            </type>
        </parameters>
        <text>Tigase</text>
    </org>
    <bday>
        <date>1966-08-06</date>
    </bday>
    <adr>
        <parameters>
            <type>
                <text>work</text>
                <text>voice</text>
            </type>
            <pref>
                <integer>1</integer>
            </pref>
        </parameters>
        <ext>Suite 600</ext>
        <street>1899 Wynkoop Street</street>
        <locality>Denver</locality>
        <region>CO</region>
        <code>80202</code>
        <country>USA</country>
    </adr>
    <adr>
        <parameters>
            <type>
                <text>home</text>
            </type>
        </parameters>
        <ext></ext>
        <street></street>
        <locality>Parker</locality>
        <region>CO</region>
        <code>80138</code>
        <country>USA</country>
    </adr>
    <tel>
        <parameters>
            <type>
                <text>work</text>
                <text>text</text>
                <text>voice</text>
                <text>cell</text>
                <text>video</text>
            </type>
        </parameters>
        <uri>tel:+1-418-262-6501</uri>
    </tel>
    <note>
        <text>
            My co-author on XEP-0292. She's cool!
        </text>
    </note>
    <impp>
        <parameters>
            <type>
                <text>work</text>
            </type>
        </parameters>
        <uri>xmpp:samizzi@cisco.com</uri>
    </impp>
    <photo>
        <uri>http://stpeter.im/images/stpeter_oscon.jpg</uri>
    </photo>
    <photo>
        <uri>data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAAMI2lDQ1BJQ0MgUHJvZmlsZQAASImVVwdUk8kWnr+kktACoUg</uri>
    </photo>
    <email>
        <parameters>
            <type>
                <text>work</text>
            </type>
        </parameters>
        <text>psaintan@cisco.com</text>
    </email>
    <email>
        <parameters>
            <type>
                <text>home</text>
            </type>
        </parameters>
        <text>stpeter@jabber.org</text>
    </email>
    <tz>
        <text>America/Chicago</text>
    </tz>
</vcard>
	""".trimIndent()

	@Test
	fun testBDay() {
		val vCard = VCard(parseXML(sample))
		assertFalse(vCard.isEmpty())
		assertEquals("1966-08-06", vCard.birthday)
	}

	@Test
	fun testTimeZone() {
		val vCard = VCard(parseXML(sample))
		assertFalse(vCard.isEmpty())
		assertEquals("America/Chicago", vCard.timeZone)
	}

	@Test
	fun testEmails() {
		val vCard = VCard(parseXML(sample))
		assertFalse(vCard.isEmpty())

		assertNotNull(vCard.emails)[0].let { email ->
			assertEquals(1, email.parameters.types.size)
			assertTrue(email.parameters.types.contains("work"))
			assertEquals("psaintan@cisco.com", email.text)
		}
		assertNotNull(vCard.emails)[1].let { email ->
			assertEquals(1, email.parameters.types.size)
			assertTrue(email.parameters.types.contains("home"))
			assertEquals("stpeter@jabber.org", email.text)
		}
	}

	@Test
	fun testAddress() {
		val vCard = VCard(parseXML(sample))
		assertFalse(vCard.isEmpty())

		assertEquals(2, vCard.addresses.size)
		assertNotNull(vCard.addresses)[0].let { adr ->
			assertEquals("USA", adr.country)
			assertEquals("80202", adr.code)
			assertEquals("CO", adr.region)
			assertEquals("Denver", adr.locality)
			assertEquals("1899 Wynkoop Street", adr.street)
			assertEquals("Suite 600", adr.ext)
			assertEquals(1, adr.parameters.pref)
			assertEquals(2, adr.parameters.types.size)
			assertTrue(adr.parameters.types.contains("work"))
			assertTrue(adr.parameters.types.contains("voice"))
		}
		assertNotNull(vCard.addresses)[1].let { adr ->
			assertEquals("USA", adr.country)
			assertEquals("80138", adr.code)
			assertEquals("CO", adr.region)
			assertEquals("Parker", adr.locality)
			assertNull(adr.street)
			assertNull(adr.ext)
			assertNull(adr.parameters.pref)
			assertEquals(1, adr.parameters.types.size)
			assertTrue(adr.parameters.types.contains("home"))
		}

	}

	@Test
	fun testProperties() {
		val vCard = VCard(parseXML(sample))
		assertFalse(vCard.isEmpty())

		println(vCard.element.getAsString())

		assertEquals("Samantha Mizzi", vCard.formattedName)
		vCard.formattedName = "Genowefa"
		assertEquals("Genowefa", vCard.formattedName)

		assertEquals("Mizzi", vCard.structuredName?.surname)
		assertEquals("Samantha", vCard.structuredName?.given)
		assertNull(vCard.structuredName?.additional)

		assertNotNull(vCard.photos).let { photos ->
			assertEquals(2, photos.size)
			assertTrue(photos[0] is Photo.PhotoUri)
			assertTrue(photos[1] is Photo.PhotoData)

			assertEquals("http://stpeter.im/images/stpeter_oscon.jpg", photos[0].uri)

			assertEquals("image/png", (photos[1] as Photo.PhotoData).imageType)
			assertEquals(
				"iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAAMI2lDQ1BJQ0MgUHJvZmlsZQAASImVVwdUk8kWnr+kktACoUg",
				(photos[1] as Photo.PhotoData).data
			)
		}

		val org = vCard.organizations
		assertEquals(2, org.count())

		assertEquals("Cisco", org.get(0).name)
		assertEquals("Tigase", org.get(1).name)
		assertEquals(2, org.get(1).parameters.pref)

		org.get(0).name = "Test"
		assertEquals("Test", vCard.element.findChild("vcard", "org", "text")?.value)

		val tel = vCard.telephones.first()
		assertEquals("tel:+1-418-262-6501", tel.uri)
		assertTrue(tel.parameters.types.contains("cell"))

		tel.parameters.types = listOf("1", "2", "3")

		assertFalse(tel.parameters.types.contains("cell"))
		assertTrue(tel.parameters.types.contains("1"))
		assertTrue(tel.parameters.types.contains("2"))
		assertTrue(tel.parameters.types.contains("3"))

		tel.parameters.types = emptyList()
		tel.parameters.pref = null

		println(tel.element.getAsString())

	}

	@Test
	fun testEmptyVCard() {
		val vCard = VCard(element("vcard") {
			xmlns = VCardModule.XMLNS
		}).apply {
			formattedName = "Genowefa"
			nickname = ""
			structuredName = StructuredName().apply {
				additional = ""
			}
			organizations = listOf(Organization().apply { })
		}
		println(vCard.element.getAsString())
		assertEquals(1, vCard.element.children.size)
		assertEquals("fn", vCard.element.children[0].name)
	}

	@Test
	fun testFormattedName() {
		val vCard = VCard(element("vcard") {
			xmlns = VCardModule.XMLNS
		})
		vCard.formattedName = "Genowefa"
		assertEquals("Genowefa", vCard.formattedName)
	}

	@Test
	fun testOrg() {
		val vCard = VCard(element("vcard") {
			xmlns = VCardModule.XMLNS
		})

		val o1 = Organization()
		o1.name = "o1"
		o1.parameters.pref = 4

		vCard.organizations = listOf(o1)

		println(o1.element.getAsString())

		assertEquals("o1", vCard.element.findChild("vcard", "org", "text")?.value)
		assertEquals("4", vCard.element.findChild("vcard", "org", "parameters", "pref", "integer")?.value)

		vCard.organizations = listOf()
		assertNull(vCard.element.findChild("vcard", "org", "text")?.value)

	}

	@Test
	fun testStructuredName() {
		val vCard = VCard(element("vcard") {
			xmlns = VCardModule.XMLNS
		})

		val sn = StructuredName()
		sn.surname = "Saint-Andre"
		sn.given = "Peter"
		vCard.structuredName = sn

		assertEquals("Saint-Andre", vCard.element.findChild("vcard", "n", "surname")?.value)
		assertEquals("Peter", vCard.element.findChild("vcard", "n", "given")?.value)

	}

}