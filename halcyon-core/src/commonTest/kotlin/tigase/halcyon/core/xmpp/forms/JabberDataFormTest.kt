/*
 * Tigase Halcyon XMPP Library
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
package tigase.halcyon.core.xmpp.forms

import tigase.halcyon.core.xml.element
import kotlin.test.*

class JabberDataFormTest {

	@Test
	fun testFieldValue() {
		val field = Field(element("field") {
			attribute("type", "text-single")
			attribute("val", "test")
			"required"{}
			"value"{ +"123" }
		})
		assertEquals("123", field.fieldValue)
		assertEquals(listOf("123"), field.fieldValues)

		field.fieldValue = "9871"
		assertEquals("9871", field.fieldValue)
		assertEquals(listOf("9871"), field.fieldValues)

		field.fieldValues = listOf("a123", "b321", "c989")
		assertEquals("a123, b321, c989", field.fieldValue)
		assertEquals(listOf("a123", "b321", "c989"), field.fieldValues)

		field.fieldValue = "345"
		assertEquals(listOf("345"), field.fieldValues)
		assertEquals("345", field.fieldValue)

		field.fieldValue = null
		assertEquals(listOf(), field.fieldValues)
		assertEquals(null, field.fieldValue)
		assertEquals(0, field.element.children.filter { element -> element.name == "value" }.count())
	}

	@Test
	fun testFieldRequired() {
		val field = Field(element("field") {
			attribute("type", "text-single")
			attribute("val", "test")
			"required"{}
		})
		assertTrue(field.fieldRequired)
		assertEquals(1, field.element.children.count { it.name == "required" })
		field.fieldRequired = true
		assertTrue(field.fieldRequired)
		assertEquals(1, field.element.children.count { it.name == "required" })
		field.fieldRequired = false
		assertFalse(field.fieldRequired)
		assertEquals(0, field.element.children.count { it.name == "required" })
		field.fieldRequired = true
		assertTrue(field.fieldRequired)
		assertEquals(1, field.element.children.count { it.name == "required" })
	}

	@Test
	fun testType() {
		val form = JabberDataForm(FormType.Submit)
		assertEquals(FormType.Submit, form.type)
		assertEquals("submit", form.element.attributes["type"])
		form.type = FormType.Form
		assertEquals(FormType.Form, form.type)
		assertEquals("form", form.element.attributes["type"])
	}

	@Test
	fun testTypeFromElement() {
		val form = JabberDataForm(element("x") {
			xmlns = "jabber:x:data"
			attribute("type", "submit")
		})
		assertEquals(FormType.Submit, form.type)
		assertEquals("submit", form.element.attributes["type"])
	}

	@Test
	fun testClearForm() {
		val form = createSampleForm()
		assertEquals(14, form.element.children.count())
		form.clearForm()
		assertEquals(0, form.element.children.count())
	}

	@Test
	fun testCreateSubmitForm() {
		val form = createSampleForm()

		val submitingForm = form.createSubmitForm()
		assertNotNull(submitingForm)

		assertEquals(8, submitingForm.children.filter { it.name == "field" }.count())
		val featuresField = submitingForm.children.first { it.attributes["var"] == "features" }
		assertNotNull(featuresField)
		assertNull(featuresField.attributes["label"])
		assertEquals(2, featuresField.getChildren("value").count())
		assertEquals(0, featuresField.getChildren("option").count())

		val passwordField = submitingForm.children.first { it.attributes["var"] == "password" }
		assertNotNull(passwordField)
		assertEquals(1, passwordField.getChildren("value").count(), "Empty element value is expected")
	}

	private fun createSampleForm(): JabberDataForm = JabberDataForm(element("x") {
		xmlns = "jabber:x:data"
		attribute("type", "form")
		"title"{ +"Bot Configuration" }
		"instructions"{ +"Fill out this form to configure your new bot!" }
		"field"{
			attribute("type", "hidden")
			attribute("var", "FORM_TYPE")
			"value"{ +"jabber:bot" }
		}
		"field"{
			attribute("type", "fixed")
			"value"{ +"Section 1: Bot Info" }
		}
		"field"{
			attribute("type", "text-single")
			attribute("var", "botname")
			attribute("label", "The name of your bot")
		}
		"field"{
			attribute("type", "text-multi")
			attribute("var", "description")
			attribute("label", "Helpful description of your bot")
		}
		"field"{
			attribute("type", "boolean")
			attribute("var", "public")
			attribute("label", "Public bot?")
			"required"{}
		}
		"field"{
			attribute("type", "text-private")
			attribute("var", "password")
			attribute("label", "Password for special access")
		}
		"field"{
			attribute("type", "fixed")
			"value"{ +"Section 2: Features" }
		}
		"field"{
			attribute("type", "list-multi")
			attribute("var", "features")
			attribute("label", "What features will the bot support?")
			"option"{
				attribute("label", "Contests")
				"value"{ +"contests" }
			}
			"option"{
				attribute("label", "News")
				"value"{ +"news" }
			}
			"option"{
				attribute("label", "Polls")
				"value"{ +"polls" }
			}
			"option"{
				attribute("label", "Reminders")
				"value"{ +"reminders" }
			}
			"option"{
				attribute("label", "Search")
				"value"{ +"search" }
			}
			"value"{ +"news" }
			"value"{ +"search" }
		}
		"field"{
			attribute("type", "fixed")
			"value"{ +"Section 3: Subscriber List" }
		}
		"field"{
			attribute("type", "list-single")
			attribute("var", "maxsubs")
			attribute("label", "Maximum number of subscribers")
			"value"{ +"20" }
			"option"{
				attribute("label", "10")
				"value"{ +"10" }
			}
			"option"{
				attribute("label", "20")
				"value"{ +"20" }
			}
			"option"{
				attribute("label", "30")
				"value"{ +"30" }
			}
			"option"{
				attribute("label", "50")
				"value"{ +"50" }
			}
			"option"{
				attribute("label", "100")
				"value"{ +"100" }
			}
			"option"{
				attribute("label", "None")
				"value"{ +"none" }
			}
		}
		"field"{
			attribute("type", "fixed")
			"value"{ +"Section 4: Invitations" }
		}
		"field"{
			attribute("type", "jid-multi")
			attribute("var", "invitelist")
			attribute("label", "People to invite")
			"desc"{ +"Tell all your friends about your new bot!" }
		}
	})

	@Test
	fun testFormCreate() {
		val form = JabberDataForm(FormType.Form)
		form.addField("a", FieldType.Hidden).fieldValue = "abc"
		form.addField(null, FieldType.Fixed).fieldValue = "TEST"

		val pwd = form.addField("password", FieldType.TextPrivate)
		pwd.fieldValue = "123"
		pwd.fieldDesc = "Password here"
		pwd.fieldRequired = true

		assertEquals("123", form.getFieldByVar("password")?.fieldValue)
		assertEquals("Password here", form.getFieldByVar("password")?.fieldDesc)
	}

	@Test
	fun testCreateFillSubmit() {
		val form = createSampleForm()

		form.getFieldByVar("features")?.fieldValues = listOf("reminders", "polls")
		form.getFieldByVar("password")?.fieldValue = "1234567890"

		val submit = form.createSubmitForm()

		assertEquals(listOf("reminders", "polls"),
					 submit.children.first { it.attributes["var"] == "features" }.children.filter { it.name == "value" }
						 .mapNotNull { it.value })

		assertEquals(
			"1234567890", submit.children.first { it.attributes["var"] == "password" }.getFirstChild("value")?.value
		)

	}

}