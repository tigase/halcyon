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
package tigase.halcyon.core.xmpp.stanzas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

class PresenceTest {

    @Test
    fun testTypeUnavailable() {
        val e = element("presence") {
            attribute("type", "unavailable")
        }
        val p = wrap<Presence>(e)
        assertEquals(PresenceType.Unavailable, p.type)
    }

    @Test
    fun testTypeSet() {
        val p = presence { type = PresenceType.Error }
        assertEquals(PresenceType.Error, p.type)
        assertEquals("error", p.attributes["type"])
        p.type = PresenceType.Probe
        assertEquals(PresenceType.Probe, p.type)
        assertEquals("probe", p.attributes["type"])
        p.type = null
        assertNull(p.attributes["type"])
    }

    @Test
    fun testShowSet() {
        val p = presence {
            "show" { +"dnd" }
        }
        assertEquals(Show.DnD, p.show)
        p.show = Show.Away
        assertEquals(Show.Away, p.show)
        assertEquals("away", p.getFirstChild("show")?.value)
    }

    @Test
    fun testPrioritySet() {
        val p = presence {}
        p.priority = 17
        assertEquals("17", p.getFirstChild("priority")?.value)
        assertEquals(17, p.priority)
    }

    @Test
    fun testTypeNull() {
        val e = element("presence") { }
        val p = wrap<Presence>(e)
        assertNull(p.type)
    }

    @Test
    fun testTypeUnknown() {
        val e = element("presence") {
            attribute("type", "x")
        }
        val p = wrap<Presence>(e)
        try {
            p.type
            fail("Exception should be throw")
        } catch (e: XMPPException) {
            assertEquals(ErrorCondition.BadRequest, e.condition)
        }
    }
}
