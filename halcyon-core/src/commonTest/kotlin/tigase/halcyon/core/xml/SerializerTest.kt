package tigase.halcyon.core.xml

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tigase.halcyon.core.xmpp.stanzas.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SerializerTest {

    @Test
    fun element_test() {
        val data = element("x") {
            attribute("to", "romeo@example.net")
            attribute("from", "juliet@example.com/balcony")
            attribute("type", "chat")
            attribute("xml:lang", "en")
            "body" {
                +"Wherefore art thou, Romeo?"
            }
            "thread" { +"e0ffe42b28561960c6b12b944a092794b9683a38" }
            element("subject") {
                value = "I implore you!"
            }
            "x" {
                xmlns = "test:urn"
                "presence" {
                    +"dnd"
                }
            }
        }

        val string = Json.encodeToString(data)
        println(string)
        val x = Json.decodeFromString<ElementImpl>(string)

        assertEquals(data, x)
        assertEquals(data.getAsString(), x.getAsString())
    }

    @Test
    fun message_test() {
        val data = message {
            attribute("to", "romeo@example.net")
            attribute("from", "juliet@example.com/balcony")
            attribute("type", "chat")
            attribute("xml:lang", "en")
            "body" {
                +"Wherefore art thou, Romeo?"
            }
            "thread" { +"e0ffe42b28561960c6b12b944a092794b9683a38" }
            element("subject") {
                value = "I implore you!"
            }
            "x" {
                xmlns = "test:urn"
                "presence" {
                    +"dnd"
                }
            }
        }

        val string = Json.encodeToString(data)
        val x = Json.decodeFromString<Message>(string)

        println(string)
        println(x)

        assertEquals(data, x)
        assertEquals(data.getAsString(), x.getAsString())
    }


    @Test
    fun iq_test() {
        val data = iq {
            type = IQType.Get
            attribute("to", "romeo@example.net")
            attribute("from", "juliet@example.com/balcony")
            attribute("xml:lang", "en")
            "body" {
                +"Wherefore art thou, Romeo?"
            }
            "thread" { +"e0ffe42b28561960c6b12b944a092794b9683a38" }
            element("subject") {
                value = "I implore you!"
            }
            "x" {
                xmlns = "test:urn"
                "presence" {
                    +"dnd"
                }
            }
        }

        val string = Json.encodeToString(data)
        val x = Json.decodeFromString<IQ>(string)

        assertEquals(data, x)
        assertEquals(data.getAsString(), x.getAsString())
    }

    @Test
    fun invalid_cast_test() {
        val e = Json.decodeFromString<Message>("{\"name\":\"message\"}")
        assertIs<Message>(e)
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString<Presence>("{\"name\":\"message\"}")
        }
        assertIs<Message>(
            Json.decodeFromString<Stanza<MessageType>>("{\"name\":\"message\"}")
        )
    }

    @Test
    fun empty_stanza() {
        val data = element("message") {}
        val string = Json.encodeToString(data)
        assertEquals("{\"name\":\"message\"}", string)
        val x = Json.decodeFromString<Element>(string)
        assertEquals(data, x)
        assertEquals(data.getAsString(), x.getAsString())
    }

    @Test
    fun empty_stanza_xmlns() {
        val data = element("message") { xmlns = "test" }
        val string = Json.encodeToString(data)
        println(string)
        val x = Json.decodeFromString<Element>(string)
        assertEquals(data, x)
        assertEquals(data.getAsString(), x.getAsString())
    }

    @Test
    fun value_stanza_xmlns() {
        val data = element("message") {
            xmlns = "test"
            value = "xxx"
        }
        val string = Json.encodeToString(data)
        println(string)
        val x = Json.decodeFromString<Element>(string)
        assertEquals(data, x)
        assertEquals(data.getAsString(), x.getAsString())
    }

    @Test
    fun one_kid_stanza_xmlns() {
        val data = element("message") {
            xmlns = "test"
            value = "xxx"
            "sub" {
                xmlns = "a"
            }
        }
        val string = Json.encodeToString(data)
        println(string)
        val x = Json.decodeFromString<Element>(string)
        assertEquals(data, x)
        assertEquals(data.getAsString(), x.getAsString())
    }

    @Test
    fun presence_test() {
        val data = presence {
            type = PresenceType.Subscribe
            attribute("to", "romeo@example.net")
            attribute("from", "juliet@example.com/balcony")
            attribute("xml:lang", "en")
            "body" {
                +"Wherefore art thou, Romeo?"
            }
            "thread" { +"e0ffe42b28561960c6b12b944a092794b9683a38" }
            element("subject") {
                value = "I implore you!"
            }
            "x" {
                xmlns = "test:urn"
                "presence" {
                    +"dnd"
                }
            }
        }

        val string = Json.encodeToString(data)
        val x = Json.decodeFromString<Presence>(string)

        assertEquals(data, x)
        assertEquals(data.getAsString(), x.getAsString())
    }


}