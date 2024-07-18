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
package tigase.halcyon.core.xmpp.forms

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import kotlin.test.*

fun assertContains(expected: Element, actual: Element, message: String? = null) {
    fun check(expected: Element, actual: Element): Boolean {
        if (expected.name != actual.name) return false
        if (expected.value != null && expected.value != actual.value) return false
        if (!expected.attributes.filter { it.key != "id" }
                .all { e -> actual.attributes[e.key] == e.value }) return false
        if (!expected.children.all { e ->
                actual.children.any { a -> check(e, a) }
            }) return false
        return true
    }

    fun messagePrefix(message: String?) = if (message == null) "" else "$message. "

    if (!check(expected, actual)) {
        fail(messagePrefix(message) + "Expected all of ${expected.getAsString()}, actual ${actual.getAsString()}.")
    }
}

class JabberDataFormTest {

    @Test
    fun serialization_test() {
        val form = createSampleForm()
        val string = Json.encodeToString(form)
        val x = Json.decodeFromString<JabberDataForm>(string)
        assertIs<JabberDataForm>(x)
        assertEquals(form.element, x.element)
    }

    @Test
    fun testFieldValue() {
        val field = Field(element("field") {
            attribute("type", "text-single")
            attribute("val", "test")
            "required" {}
            "value" { +"123" }
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
        assertEquals(0,
            field.element.children.filter { element -> element.name == "value" }
                .count())
    }

    @Test
    fun testFieldRequired() {
        val field = Field(element("field") {
            attribute("type", "text-single")
            attribute("val", "test")
            "required" {}
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
        val form = JabberDataForm.create(FormType.Submit)
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

        assertEquals(8,
            submitingForm.children.filter { it.name == "field" }
                .count())
        val featuresField = submitingForm.children.first { it.attributes["var"] == "features" }
        assertNotNull(featuresField)
        assertNull(featuresField.attributes["label"])
        assertEquals(
            2,
            featuresField.getChildren("value")
                .count()
        )
        assertEquals(
            0,
            featuresField.getChildren("option")
                .count()
        )

        val passwordField = submitingForm.children.first { it.attributes["var"] == "password" }
        assertNotNull(passwordField)
        assertEquals(
            1,
            passwordField.getChildren("value")
                .count(),
            "Empty element value is expected"
        )
    }

    private fun createSampleForm(): JabberDataForm = JabberDataForm(element("x") {
        xmlns = "jabber:x:data"
        attribute("type", "form")
        "title" { +"Bot Configuration" }
        "instructions" { +"Fill out this form to configure your new bot!" }
        "field" {
            attribute("type", "hidden")
            attribute("var", "FORM_TYPE")
            "value" { +"jabber:bot" }
        }
        "field" {
            attribute("type", "fixed")
            "value" { +"Section 1: Bot Info" }
        }
        "field" {
            attribute("type", "text-single")
            attribute("var", "botname")
            attribute("label", "The name of your bot")
        }
        "field" {
            attribute("type", "text-multi")
            attribute("var", "description")
            attribute("label", "Helpful description of your bot")
        }
        "field" {
            attribute("type", "boolean")
            attribute("var", "public")
            attribute("label", "Public bot?")
            "required" {}
        }
        "field" {
            attribute("type", "text-private")
            attribute("var", "password")
            attribute("label", "Password for special access")
        }
        "field" {
            attribute("type", "fixed")
            "value" { +"Section 2: Features" }
        }
        "field" {
            attribute("type", "list-multi")
            attribute("var", "features")
            attribute("label", "What features will the bot support?")
            "option" {
                attribute("label", "Contests")
                "value" { +"contests" }
            }
            "option" {
                attribute("label", "News")
                "value" { +"news" }
            }
            "option" {
                attribute("label", "Polls")
                "value" { +"polls" }
            }
            "option" {
                attribute("label", "Reminders")
                "value" { +"reminders" }
            }
            "option" {
                attribute("label", "Search")
                "value" { +"search" }
            }
            "value" { +"news" }
            "value" { +"search" }
        }
        "field" {
            attribute("type", "fixed")
            "value" { +"Section 3: Subscriber List" }
        }
        "field" {
            attribute("type", "list-single")
            attribute("var", "maxsubs")
            attribute("label", "Maximum number of subscribers")
            "value" { +"20" }
            "option" {
                attribute("label", "10")
                "value" { +"10" }
            }
            "option" {
                attribute("label", "20")
                "value" { +"20" }
            }
            "option" {
                attribute("label", "30")
                "value" { +"30" }
            }
            "option" {
                attribute("label", "50")
                "value" { +"50" }
            }
            "option" {
                attribute("label", "100")
                "value" { +"100" }
            }
            "option" {
                attribute("label", "None")
                "value" { +"none" }
            }
        }
        "field" {
            attribute("type", "fixed")
            "value" { +"Section 4: Invitations" }
        }
        "field" {
            attribute("type", "jid-multi")
            attribute("var", "invitelist")
            attribute("label", "People to invite")
            "desc" { +"Tell all your friends about your new bot!" }
        }
    })

    @Test
    fun testFormCreate() {
        val form = JabberDataForm.create(FormType.Form)
        form.title = "TeSt"
        form.addField("a", FieldType.Hidden).fieldValue = "abc"
        form.addField(null, FieldType.Fixed).fieldValue = "TEST"
        form.addField("password", FieldType.TextPrivate)
            .apply {
                fieldValue = "123"
                fieldDesc = "Password here"
                fieldRequired = true
            }

        assertEquals("TeSt", form.element.getFirstChild("title")?.value)
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

        assertEquals("1234567890",
            submit.children.first { it.attributes["var"] == "password" }
                .getFirstChild("value")?.value)
    }

    @Test
    fun testMultipleItemsRead() {
        val form = JabberDataForm(element("x") {
            xmlns = "jabber:x:data"
            attribute("type", "result")
            "title" { +"Bot Configuration" }
            "reported" {
                "field" { attributes["var"] = "name" }
                "field" { attributes["var"] = "url" }
            }
            "item" {
                "field" {
                    attributes["var"] = "name"
                    "value" { +"Comune di Verona - Benvenuti nel sito ufficiale" }
                }
                "field" {
                    attributes["var"] = "url"
                    "value" { +"http://www.comune.verona.it/" }
                }
            }
            "item" {
                "field" {
                    attributes["var"] = "name"
                    "value" { +"Universita degli Studi di Verona - Home Page" }
                }
                "field" {
                    attributes["var"] = "url"
                    "value" { +"http://www.univr.it/" }
                }
            }
            "item" {
                "field" {
                    attributes["var"] = "name"
                    "value" { +"Aeroporti del Garda" }
                }
                "field" {
                    attributes["var"] = "url"
                    "value" { +"http://www.aeroportoverona.it/" }
                }
            }
            "item" {
                "field" {
                    attributes["var"] = "name"
                    "value" { +"Veronafiere - fiera di Verona" }
                }
                "field" {
                    attributes["var"] = "url"
                    "value" { +"http://www.veronafiere.it/" }
                }
            }
        })
        assertTrue(form.multipleItems)
        assertEquals(4, form.itemsCount)
        assertEquals(2, form.getReportedColumns().size)
        assertEquals("name", form.getReportedColumns()[0].fieldName)
        assertEquals("url", form.getReportedColumns()[1].fieldName)

        val items = form.getItems()
        assertEquals(4, items.size)

        assertEquals(
            "Comune di Verona - Benvenuti nel sito ufficiale",
            items[0].getValue("name").fieldValue
        )
        assertEquals("http://www.comune.verona.it/", items[0].getValue("url").fieldValue)
        assertEquals("Veronafiere - fiera di Verona", items[3].getValue("name").fieldValue)
        assertEquals("http://www.veronafiere.it/", items[3].getValue("url").fieldValue)

        assertFailsWith<IllegalStateException> { items[0].getValue("not exists") }
    }

    @Test
    fun testMultipleItemsCreate() {
        val form = JabberDataForm.create(FormType.Result)
        form.title = "Bot Configuration"
        form.setReportedColumns(listOf(Field.create("name"), Field.create("url")))

        assertTrue(form.multipleItems)
        assertEquals(2, form.getReportedColumns().size)

        form.addItem(
            listOf(Field.create("name")
                .apply { fieldValue = "Comune di Verona - Benvenuti nel sito ufficiale" },
                Field.create("url")
                    .apply { fieldValue = "http://www.comune.verona.it/" })
        )
        form.addItem(
            listOf(Field.create("name")
                .apply { fieldValue = "Universita degli Studi di Verona - Home Page" },
                Field.create("url")
                    .apply { fieldValue = "http://www.univr.it/" })
        )
        form.addItem(
            listOf(Field.create("name")
                .apply { fieldValue = "Aeroporti del Garda" },
                Field.create("url")
                    .apply { fieldValue = "http://www.aeroportoverona.it/" })
        )
        form.addItem(
            listOf(Field.create("name")
                .apply { fieldValue = "Veronafiere - fiera di Verona" },
                Field.create("url")
                    .apply { fieldValue = "http://www.veronafiere.it/" })
        )

        assertFailsWith<IllegalArgumentException> {
            form.addItem(
                listOf(Field.create("name")
                    .apply { fieldValue = "1" },
                    Field.create("oops")
                        .apply { fieldValue = "2" })
            )
        }
        assertFailsWith<IllegalArgumentException> {
            form.addItem(
                listOf(Field.create("name")
                    .apply { fieldValue = "1" },
                    Field.create("url")
                        .apply { fieldValue = "2" },
                    Field.create("oops")
                        .apply { fieldValue = "3" })
            )
        }
        assertFailsWith<IllegalArgumentException> {
            form.addItem(
                listOf(Field.create("name")
                    .apply { fieldValue = "1" })
            )
        }

        assertEquals(4, form.getItems().size)

        assertContains(element("x") {
            xmlns = "jabber:x:data"
            attribute("type", "result")
            "title" { +"Bot Configuration" }
            "reported" {
                "field" { attributes["var"] = "name" }
                "field" { attributes["var"] = "url" }
            }
            "item" {
                "field" {
                    attributes["var"] = "name"
                    "value" { +"Comune di Verona - Benvenuti nel sito ufficiale" }
                }
                "field" {
                    attributes["var"] = "url"
                    "value" { +"http://www.comune.verona.it/" }
                }
            }
            "item" {
                "field" {
                    attributes["var"] = "name"
                    "value" { +"Universita degli Studi di Verona - Home Page" }
                }
                "field" {
                    attributes["var"] = "url"
                    "value" { +"http://www.univr.it/" }
                }
            }
            "item" {
                "field" {
                    attributes["var"] = "name"
                    "value" { +"Aeroporti del Garda" }
                }
                "field" {
                    attributes["var"] = "url"
                    "value" { +"http://www.aeroportoverona.it/" }
                }
            }
            "item" {
                "field" {
                    attributes["var"] = "name"
                    "value" { +"Veronafiere - fiera di Verona" }
                }
                "field" {
                    attributes["var"] = "url"
                    "value" { +"http://www.veronafiere.it/" }
                }
            }
        }, form.element)
    }
}