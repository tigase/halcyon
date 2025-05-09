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
package tigase.halcyon.core.xml

import kotlin.test.*

class ElementImplTest {

    val ea11 = element("a") {
        attribute("id", "000")
        attribute("ok", "010")
        "a" {
            xmlns = "xxx"
            +"value"
        }
        "1" {
            attribute("type", "x")
            xmlns = "xxx"
            +"something else"
        }
        "b" {
            attribute("type", "y")
            "ba" {
                "bb" {
                    +"99898"
                }
            }
        }
        "a" {
            +"value2"
        }
    } as ElementImpl
    val ea12 = element("a") {
        attribute("id", "000")
        attribute("ok", "010")
        "a" {
            xmlns = "xxx"
            +"value"
        }
        "1" {
            attribute("type", "x")
            xmlns = "xxx"
            +"something else"
        }
        "b" {
            attribute("type", "y")
            "ba" {
                "bb" {
                    +"99898"
                }
            }
        }
        "a" {
            +"value2"
        }
    } as ElementImpl
    val eb11 = element("a") {
        attribute("id", "000")
        attribute("ok", "101")
        "a" {
            xmlns = "xxx"
            +"value"
        }
        "1" {
            attribute("type", "x")
            xmlns = "xxx"
            +"something else"
        }
        "b" {
            attribute("type", "y")
            "ba" {
                "bb" {
                    +"99898"
                }
            }
        }
        "a" {
            +"value2"
        }
    } as ElementImpl
    val ec11 = element("a") {
        attribute("id", "000")
        attribute("ok", "010")
        "a" {
            xmlns = "xxx"
            +"value"
        }
        "1" {
            attribute("type", "x")
            xmlns = "xxx"
            +"something else"
        }
        "b" {
            attribute("type", "y")
            "ba" {
                "bb" {
                    +"99890"
                }
            }
        }
        "a" {
            +"value2"
        }
    } as ElementImpl

    @Test
    fun testFirstChildAndSibling() {
        val a = ea11.getFirstChild("a")
        assertEquals("a", a!!.name)
        val b = a.getNextSibling()
        assertEquals("1", b!!.name)

        assertEquals("a", ea11.getFirstChild()!!.name)
    }

    @Test
    fun testFindChild() {
        assertEquals("1", ea11.findChild("a", "1")!!.name)
        assertEquals("x", ea11.findChild("a", "1")!!.attributes["type"])
        assertEquals("a", ea11.findChild("a", "a")!!.name)
        assertEquals("99898", ea11.findChild("a", "b", "ba", "bb")!!.value)
    }

    @Test
    fun testGetChildAfter() {
        val ch = ea11.getFirstChild("1")
        assertEquals("1", ch!!.name)
        assertEquals("b", ea11.getChildAfter(ch).name)
    }

    @Test
    fun testChildren() {
        val list = ea11.getChildren("a")
        assertEquals(2, list.count())
        assertEquals("a", list[0].name)
        assertEquals("a", list[1].name)
    }

    @Test
    fun testGetChildrenNS() {
        val list = ea11.getChildrenNS("xxx")
        assertEquals(2, list.count())
        assertEquals("a", list[0].name)
        assertEquals("1", list[1].name)
    }

    @Test
    fun testGetChildrenNameNS() {
        val e = ea11.getChildrenNS("a", "xxx")
        assertEquals("a", e!!.name)
    }

    @Test
    fun testEquals() {
        assertEquals(ea11, ea11)
        assertEquals(ea11, ea12)
        assertEquals(ea12, ea11)

        assertNotEquals(ea11, eb11)
        assertNotEquals(ea11, ec11)
        assertNotEquals(eb11, ec11)

        assertEquals(ea11, ea12)
        assertTrue(ea11.equals(ea12))
        assertTrue(ea12.equals(ea11))

        assertEquals(ea12, ea11)
        assertEquals(ea11, ea11)
        assertEquals(ea11, ea12)

        assertNotEquals(ea12, eb11)
        assertNotEquals(ea11, ec11)
        assertNotEquals(eb11, ec11)

        assertEquals(ea11.hashCode(), ea11.hashCode())
        assertEquals(ea11.hashCode(), ea12.hashCode())
    }

    private fun createElement(): Element {
        val b = ElementBuilder.create("message")
            .attribute("to", "romeo@example.net")
            .attribute("from", "juliet@example.com/balcony")
            .attribute("type", "chat")
            .child("subject")
            .value("I implore you!")
            .up()
            .child("body")
            .value("Wherefore art thou, Romeo?")
            .up()
            .child("thread")
            .value("e0ffe42b28561960c6b12b944a092794b9683a38")
            .up()
            .child("x")
            .value("tigase:offline")
            .xmlns("tigase")
        return b.build()
    }

    @Test
    fun testFindChild2() {
        val element = createElement()

        var nullElement = element.findChild("message", "missing")
        assertNull(nullElement)

        nullElement = element.findChild("x", "body")
        assertNull(nullElement)

        val c = element.findChild("message", "body")
        assertNotNull(c)
        assertEquals("body", c.name)
        assertEquals("Wherefore art thou, Romeo?", c.value)
    }

    @Test
    fun testNextSibling() {
        val element = createElement()
        val c = element.findChild("message", "body")
        assertNotNull(c)
        assertEquals("body", c.name)
        assertEquals("thread", c.getNextSibling()!!.name)
    }

    @Test
    fun testGetAsString() {
        val e1 = element("a") {
            attributes["id"] = "123"
            "b" {
                "c" {
                    "d" {
                        +"test123"
                    }
                }
            }
        }
        val e2 = element("a") {
            attributes["a"] = "b"
            +"test321"
        }
        assertEquals("<a id=\"123\"><b><c><d>test123</d></c></b></a>", e1.getAsString())
        assertEquals("<a a=\"b\">test321</a>", e2.getAsString())

        val e3 = element("a") {
            attributes["id"] = "123"
            "b" {
                "c" {
                    "d" {
                        +"test123"
                    }
                    "e" {
                        +"test123"
                    }
                    "f" {
                        +"test123"
                    }
                }
            }
        }
        assertEquals(
            "<a id=\"123\"><b><c><d>test123</d><e>test123</e><f>test123</f></c></b></a>",
            e3.getAsString()
        )
    }
}
