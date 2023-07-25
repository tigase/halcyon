package tigase.halcyon.core.xml

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tigase.halcyon.core.xmpp.stanzas.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializerTest {

	val format = Json {
		ignoreUnknownKeys = true
		serializersModule = HalcyonSerializerModule
	}

	@Test
	fun element_test() {
		val data = element("message") {
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

		val string = format.encodeToString(data)
		val x = format.decodeFromString<ElementImpl>(string)

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

		val string = format.encodeToString(data)
		val x = format.decodeFromString<Message>(string)

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

		val string = format.encodeToString(data)
		val x = format.decodeFromString<IQ>(string)

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

		val string = format.encodeToString(data)
		val x = format.decodeFromString<Presence>(string)

		assertEquals(data, x)
		assertEquals(data.getAsString(), x.getAsString())
	}

}