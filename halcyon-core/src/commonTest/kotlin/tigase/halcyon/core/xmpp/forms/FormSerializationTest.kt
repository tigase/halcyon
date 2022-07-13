package tigase.halcyon.core.xmpp.forms

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.modules.muc.State
import tigase.halcyon.core.xmpp.modules.pubsub.Affiliation
import tigase.halcyon.core.xmpp.toBareJID
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertEquals

@SerializableDataForm
class SampleForm : DataFormWrapper() {

	@FormField("text")
	var text: String = ""

	@FormField("int-num")
	var intNum: Int = 0

	@FormField("enum-xmpp")
	var enumXmpp: FieldType = FieldType.Hidden

}

fun Element.asSampleForm(): SampleForm = SampleForm().apply {
	setFields()

	this.text = convertToEnum()


}

fun SampleForm.serialize(): Element = element("x") {

	xmlns = DataFormWrapper.XMLNS
	attributes["type"] = FormType.Submit.xmppValue

	(fields["text"] ?: FieldMetadata("text", null, FieldType.TextSingle, false)).let { f ->
		addChild(createFieldElement(this@serialize.text, f.name, f))
	}

	FieldMetadata("int-num", null, FieldType.TextSingle, false).let { f ->
		addChild(createFieldElement(this@serialize.intNum, f.name, f))
	}
	FieldMetadata("enum-xmpp", null, FieldType.ListSingle, false).let { f ->
		addChild(createFieldElement(this@serialize.enumXmpp, f.name, f))
	}

}

class FormSerializationTest {

	private fun createTestField(value: Any): String = createFieldElement(
		value, "field_name", FieldMetadata("field_name", null, FieldType.TextMulti, false)
	).children.map { it.value }
		.joinToString {
			it ?: "-"
		}

	@Test
	fun testCreateFieldElementManyTypes() {
		assertEquals("true", createTestField(true))
		assertEquals("false", createTestField(false))
		assertEquals("text", createTestField("text"))
		assertEquals("1", createTestField(1))
		assertEquals("1.0", createTestField(1.0))
		assertEquals("a@b.c", createTestField("a@b.c".toBareJID()))
		assertEquals("a@b.c/d", createTestField("a@b.c/d".toJID()))



		assertEquals("1, 2, 3, 4", createTestField(arrayOf(1, 2, 3, 4)))
		assertEquals("1, 2, 3, 4", createTestField(listOf(1, 2, 3, 4)))
		assertEquals("true, false, true, true", createTestField(listOf(true, false, true, true)))

		assertEquals(
			"list-multi, jid-multi, hidden",
			createTestField(arrayOf(FieldType.ListMulti, FieldType.JidMulti, FieldType.Hidden))
		)

		assertEquals("text-multi", createTestField(FieldType.TextMulti))
		assertEquals("publish-only", createTestField(Affiliation.PublishOnly))
		assertEquals("Joined", createTestField(State.Joined))
	}

	@Test
	fun testCreateFieldElement() {
		createFieldElement("As String", "field_name", FieldMetadata("field_name", null, FieldType.Hidden, false)).let {
			assertEquals("field", it.name)
			assertEquals("hidden", it.attributes["type"])
			assertEquals("field_name", it.attributes["var"])
			assertEquals("As String",
						 it.children.map { it.value }
							 .joinToString {
								 it ?: "-"
							 })
		}
		createFieldElement(true, "field_name", FieldMetadata("field_name", null, FieldType.Bool, false)).let {
			assertEquals("field", it.name)
			assertEquals("boolean", it.attributes["type"])
			assertEquals("field_name", it.attributes["var"])
			assertEquals("true",
						 it.children.map { it.value }
							 .joinToString {
								 it ?: "-"
							 })
		}
		createFieldElement("test", "field_name", FieldMetadata("field_name", null, FieldType.TextSingle, false)).let {
			assertEquals("field", it.name)
			assertEquals("text-single", it.attributes["type"])
			assertEquals("field_name", it.attributes["var"])
			assertEquals("test",
						 it.children.map { it.value }
							 .joinToString {
								 it ?: "-"
							 })
			createFieldElement(
				"test", "field_name", FieldMetadata("field_name", null, FieldType.TextPrivate, false)
			).let {
				assertEquals("field", it.name)
				assertEquals("text-private", it.attributes["type"])
				assertEquals("field_name", it.attributes["var"])
				assertEquals("test",
							 it.children.map { it.value }
								 .joinToString {
									 it ?: "-"
								 })
			}
			createFieldElement(
				"test", "field_name", FieldMetadata("field_name", null, FieldType.ListSingle, false)
			).let {
				assertEquals("field", it.name)
				assertEquals("list-single", it.attributes["type"])
				assertEquals("field_name", it.attributes["var"])
				assertEquals("test",
							 it.children.map { it.value }
								 .joinToString {
									 it ?: "-"
								 })
			}
			createFieldElement(
				arrayOf("jeden", "dwa", "trzy"),
				"field_name",
				FieldMetadata("field_name", null, FieldType.ListMulti, false)
			).let {
				assertEquals("field", it.name)
				assertEquals("list-multi", it.attributes["type"])
				assertEquals("field_name", it.attributes["var"])
				assertEquals("jeden, dwa, trzy",
							 it.children.map { it.value }
								 .joinToString {
									 it ?: "-"
								 })
			}
			createFieldElement(
				listOf("jeden", "dwa", "trzy"),
				"field_name",
				FieldMetadata("field_name", null, FieldType.ListMulti, false)
			).let {
				assertEquals("field", it.name)
				assertEquals("list-multi", it.attributes["type"])
				assertEquals("field_name", it.attributes["var"])
				assertEquals("jeden, dwa, trzy",
							 it.children.map { it.value }
								 .joinToString {
									 it ?: "-"
								 })
			}
			createFieldElement(
				1, "field_name", FieldMetadata("field_name", null, FieldType.ListMulti, false)
			).let {
				assertEquals("field", it.name)
				assertEquals("list-multi", it.attributes["type"])
				assertEquals("field_name", it.attributes["var"])
				assertEquals("1",
							 it.children.map { it.value }
								 .joinToString {
									 it ?: "-"
								 })
			}

		}
	}

	@Test
	fun testConvertToSimpleObject() {
		assertEquals(123, convertToSimpleObject(listOf("123"), Int::class))
		assertEquals("123", convertToSimpleObject(listOf("123"), String::class))


		assertEquals("123@wp.pl".toBareJID(), convertToSimpleObject(listOf("123@wp.pl"), BareJID::class))
	}

	@Test
	fun testConvertToEnum() {
		assertEquals(FieldType.ListMulti, convertToEnum(listOf("list-multi"), FieldType.values()))
		assertEquals(State.Joined, convertToEnum(listOf("Joined"), State.values()))
	}

	@Test
	fun testConvertToListOfSimpleObjects() {
		assertEquals(
			listOf(1, 2, 3, 4), convertToListOfSimpleObjects(listOf("1", "2", "3", "4"), Int::class)
		)
		assertEquals(
			listOf("1", "2", "3", "4"), convertToListOfSimpleObjects(listOf("1", "2", "3", "4"), String::class)
		)
		assertEquals(
			listOf("1123@wp.pl", "2123@wp.pl", "3123@wp.pl", "4123@wp.pl").map { it.toBareJID() },
			convertToListOfSimpleObjects(listOf("1123@wp.pl", "2123@wp.pl", "3123@wp.pl", "4123@wp.pl"), BareJID::class)
		)
	}

	@Test
	fun testConvertToListOfEnums() {
		assertEquals(
			listOf(State.RequestSent, State.Joined),
			convertToListOfEnum(listOf("RequestSent", "Joined"), State.values())
		)
		assertEquals(
			listOf(FieldType.ListMulti, FieldType.JidMulti, FieldType.Bool),
			convertToListOfEnum(listOf("list-multi", "jid-multi", "boolean"), FieldType.values())
		)

	}

}