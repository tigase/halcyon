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
package tigase.halcyon.core.modules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element

class CriteriaTest {

    val element: Element = element("iq") {
        xmlns = "jabber:client"
        attribute("to", "a@b.c")
        attribute("from", "wojtas@wp.pl")
        attribute("type", "set")
        "pubsub" {
            xmlns = "a:b"
            "publish" {
                attribute("node", "123")
                "item" {
                    attribute("id", "345")
                    value = "x"
                }
                "item" {
                    attribute("id", "456")
                }
                "item" {
                    attribute("id", "567")
                }
            }
        }
    }

    @Test
    fun testCriterion() {
        assertFalse(
            Criterion.or(Criterion.name("X"), Criterion.name("Y"))
                .match(element)
        )
        assertFalse(
            Criterion.or(Criterion.name("X"), Criterion.name("Y"))
                .match(element)
        )
        assertTrue(
            Criterion.or(Criterion.name("X"), Criterion.name("iq"))
                .match(element)
        )

        assertFalse(
            Criterion.and(Criterion.xmlns("jabber:client"), Criterion.name("X"))
                .match(element)
        )
        assertTrue(
            Criterion.and(Criterion.xmlns("jabber:client"), Criterion.name("iq"))
                .match(element)
        )

        assertTrue(
            Criterion.not(Criterion.and(Criterion.xmlns("jabber:client"), Criterion.name("X")))
                .match(element)
        )
        assertFalse(
            Criterion.not(Criterion.and(Criterion.xmlns("jabber:client"), Criterion.name("iq")))
                .match(element)
        )

        assertTrue(
            Criterion.chain(Criterion.name("iq"), Criterion.xmlns("a:b"))
                .match(element)
        )
        assertFalse(
            Criterion.chain(Criterion.name("iq"), Criterion.xmlns("a:c"))
                .match(element)
        )
        assertFalse(
            Criterion.chain(Criterion.name("presence"), Criterion.xmlns("a:b"))
                .match(element)
        )
    }
}
