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

import kotlinx.serialization.Serializable
import tigase.halcyon.core.xml.*
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

enum class FieldType(val xmppValue: String) {

	Bool("boolean"),
	Fixed("fixed"),
	Hidden("hidden"),
	JidMulti("jid-multi"),
	JidSingle("jid-single"),
	ListMulti("list-multi"),
	ListSingle("list-single"),
	TextMulti("text-multi"),
	TextPrivate("text-private"),
	TextSingle("text-single")
}

class Field(val element: Element) {

	var fieldRequired: Boolean
		get() = element.getFirstChild("required") != null
		set(value) = setRequiredInternal(value)

	var fieldLabel: String?
		get() = element.attributes["label"]
		set(value) = element.setAtt("label", value)

	var fieldType: FieldType?
		set(value) = element.setAtt("type", value?.xmppValue)
		get() = element.attributes["type"]?.let {
			FieldType.values()
				.firstOrNull { te -> te.xmppValue == it } ?: throw XMPPException(
				ErrorCondition.BadRequest, "Unknown field type '$it'"
			)
		}

	var fieldDesc: String?
		set(value) = element.setChildContent("desc", value)
		get() = element.getChildContent("desc")

	var fieldName: String?
		get() = element.attributes["var"]
		set(value) = element.setAtt("var", value)

	var fieldValues: List<String>
		set(value) = setValuesInt(value)
		get() = element.children.filter { it.name == "value" }
			.mapNotNull { it.value }
			.toList()

	var fieldValue: String?
		set(value) = setValueInt(value)
		get() = getValueInt()

	private fun getValueInt(): String? {
		val x = element.children.filter { it.name == "value" }
			.mapNotNull { it.value }
		return when {
			x.isEmpty() -> null
			x.count() == 1 -> x.first()
			else -> x.joinToString { it }
		}
	}

	private fun setValueInt(value: String?) {
		if (value != null) {
			setValuesInt(listOf(value))
		} else {
			setValuesInt(emptyList())
		}
	}

	private fun setRequiredInternal(value: Boolean) {
		val r = element.children.filter { it.name == "required" }
			.toList()
		if (!value && r.count() > 0) {
			r.forEach {
				element.remove(it)
			}
		} else if (value && r.count() == 0) {
			element.add(element("required") {})
		}
	}

	private fun setValuesInt(values: List<String>) {
		element.children.filter { it.name == "value" }
			.toList()
			.forEach {
				element.remove(it)
			}
		values.forEach { v ->
			element.add(element("value") { +v })
		}
	}

	companion object {

		fun create(varName: String?, type: FieldType? = null): Field {
			val field = element("field") {
				varName?.let { attribute("var", it) }
				type?.let { attribute("type", it.xmppValue) }
			}
			return Field(field)
		}

	}

}

enum class FormType(val xmppValue: String) {

	/**
	 * The form-processing entity is asking the form-submitting entity to complete a form.
	 */
	Form("form"),

	/**
	 * The form-submitting entity is submitting data to the form-processing entity.
	 * The submission MAY include fields that were not provided in the empty form,
	 * but the form-processing entity MUST ignore any fields that it does not understand.
	 */
	Submit("submit"),

	/**
	 * The form-submitting entity has cancelled submission of data to the form-processing entity.
	 */
	Cancel("cancel"),

	/**
	 * The form-processing entity is returning data (e.g., search results) to the form-submitting entity,
	 * or the data is a generic data set.
	 */
	Result("result");

}

@Serializable(with = JabberDataFormSerializer::class)
class JabberDataForm(val element: Element) {

	companion object {

		const val XMLNS = "jabber:x:data"

		fun create(type: FormType): JabberDataForm = JabberDataForm(element("x") { xmlns = XMLNS }).apply {
			this.type = type
		}

	}

	val multipleItems: Boolean
		get() = element.getFirstChild("reported") != null

	val itemsCount: Int
		get() = element.getChildren("item").size

	var type: FormType
		set(value) = element.attributes.set("type", value.xmppValue)
		get() = element.attributes["type"]?.let { typeName ->
			FormType.values()
				.firstOrNull { it.xmppValue == typeName } ?: throw XMPPException(
				ErrorCondition.BadRequest, "Unknown form type '$typeName'."
			)
		} ?: throw XMPPException(ErrorCondition.BadRequest, "Empty form type.")

	var title: String?
		set(value) = internalSetChildrenValue("title", value)
		get() = internalGetChildrenValue("title")

	var description: String?
		set(value) = internalSetChildrenValue("description", value)
		get() = internalGetChildrenValue("description")

	private fun internalGetChildrenValue(elementName: String): String? {
		return element.getFirstChild(elementName)?.value
	}

	private fun internalSetChildrenValue(elementName: String, value: String?) {
		var e = element.getFirstChild(elementName)
		if (e != null && value == null) element.remove(e)
		else if (e == null) {
			e = element(elementName) { +"$value" }
			element.add(e)
		} else {
			e.value = value
		}
	}

	fun getAllFields(): List<Field> {
		return element.getChildren("field")
			.map { Field(it) }
	}

	fun getFieldByVar(varName: String): Field? {
		val fieldElement = element.getChildren("field")
			.firstOrNull { field ->
				field.attributes["var"] == varName
			} ?: return null
		return Field(fieldElement)
	}

	fun addField(varName: String?, type: FieldType?): Field {
		val field = Field.create(varName, type)
		addField(field)
		return field
	}

	fun addField(field: Field): Field {
		if (field.fieldName != null) {
			element.getChildren("field")
				.firstOrNull { it.attributes["var"] == field.fieldName }
				?.let {
					element.remove(it)
				}
		}
		element.add(field.element)
		return field
	}

	fun removeField(varName: String) {
		element.getChildren("field")
			.filter { field ->
				field.attributes["var"] == varName
			}
			.forEach {
				element.remove(it)
			}
	}

	/**
	 * Removes all fields, title and description.
	 */
	fun clearForm() {
		element.children.toList()
			.forEach {
				element.remove(it)
			}
	}

	/**
	 * Creates form element ready to Submit.
	 * Form ```type``` is changed to ```submit```.
	 * All unnecessary elements (like labels, descriptions, options) are skipped.
	 * Only fields and its values are copied.
	 */
	fun createSubmitForm(): Element {
		val fields = element.getChildren("field")
			.filter { it.attributes["var"] != null }
		return element("x") {
			xmlns = XMLNS
			attribute("type", FormType.Submit.xmppValue)
			fields.forEach { field ->
				"field" {
					attribute("var", field.attributes["var"]!!)
					val vls = field.getChildren("value")
						.filter { v -> v.value != null }
					if (vls.count() == 0) {
						"value" {}
					} else {
						vls.forEach { v ->
							"value" { +v.value!! }
						}
					}
				}
			}
		}
	}

	fun getReportedColumns(): List<Field> {
		return checkNotNull(element.getFirstChild("reported")) { "This is not Multiple Items form." }.getChildren("field")
			.map { Field(it) }
	}

	fun setReportedColumns(columns: List<Field>) {
		element.getFirstChild("reported")
			?.let { r -> element.remove(r) }

		val reported = element("reported") {
			columns.forEach {
				addChild(it.element)
			}
		}
		element.add(reported)
	}

	fun addItem(fields: List<Field>) {
		val allowedFields = getReportedColumns().map { r -> r.fieldName!! }
		val fieldNames = fields.map { r -> r.fieldName!! }

		if (!allowedFields.containsAll(fieldNames) || !fieldNames.containsAll(allowedFields)) {
			throw IllegalArgumentException("Fields vars doesn't match to declared columns.")
		}

		element.add(element("item") {
			fields.forEach { addChild(it.element) }
		})
	}

	fun getItems(): List<Item> {
		val columns = getReportedColumns()
		return element.getChildren("item")
			.map { Item(columns, it) }
	}

}

class Item(private val columns: List<Field>, private val element: Element) {

	fun getValue(name: String): Field {
		val e = element.children.find { element -> element.attributes["var"] == name }
			?: throw IllegalStateException("Column $name does not exists.")
		return Field(e)
	}

}