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
package tigase.halcyon.core.xmpp.modules.vcard

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class VCardStruct(val element: Element)

class Parameters(element: Element) : VCardStruct(element) {

	var pref: Int? by VCardElement(constPath = arrayOf("parameters", "pref"),
								   path = arrayOf("integer"),
								   factory = { it.value?.toInt() })
	var types: List<String>? by VCardElementsList(
		constPath = arrayOf("parameters", "type"),
		path = arrayOf("text"),
		factory = {
			it.value!!
		})

}

class Telephone(element: Element) : VCardStruct(element) {

	val parameters = Parameters(element)

	var uri: String? by VCardElement(path = arrayOf("uri"), factory = Element::value)

}

class StructuredName(element: Element) : VCardStruct(element) {

	constructor() : this(element("n") {})

	var surname: String? by VCardElement(path = arrayOf("surname"), factory = Element::value)
	var given: String? by VCardElement(path = arrayOf("given"), factory = Element::value)
	var additional: String? by VCardElement(path = arrayOf("additional"), factory = Element::value)
}

class Organization(element: Element) : VCardStruct(element) {

	val parameters = Parameters(element)

	constructor() : this(element("org") {})

	var type: String? by VCardElement(path = arrayOf("parameters", "type", "text"), factory = Element::value)
	var name: String? by VCardElement(path = arrayOf("text"), factory = Element::value)

}

sealed class Photo(element: Element) : VCardStruct(element) {

	companion object {

		fun create(element: Element): Photo = if (element.getFirstChild("uri")?.value?.startsWith("data:") == true) {
			PhotoData(element)
		} else {
			PhotoUri(element)
		}
	}

	var uri: String? by VCardElement(path = arrayOf("uri"), factory = Element::value)

	class PhotoUri(element: Element) : Photo(element) { constructor() : this(element("photo") {})
	}

	class PhotoData(element: Element) : Photo(element) {

		constructor() : this(element("photo") {})

		private fun splitUri(): List<String?> {
			val z = uri?.split(":", ";", ",") ?: emptyList()
			return if (z.size < 4) listOf<String?>(null, null, null) else z
		}

		val imageType: String?
			get() = splitUri()[1]

		val data: String?
			get() = splitUri()[3]

		fun setData(imageType: String, base64: String) {
			uri = "data:$imageType;base64,$data"
		}

	}

}

class VCard(element: Element) : VCardStruct(element) {

	fun isEmpty(): Boolean = element.children.isEmpty()

	var structuredName: StructuredName? by VCardElement(path = arrayOf("n"), factory = ::StructuredName)
	var formattedName: String? by VCardElement(path = arrayOf("fn", "text"), factory = Element::value)
	var nickname: String? by VCardElement(path = arrayOf("nickname", "text"), factory = Element::value)
	var organizations: List<Organization>? by VCardElementsList(path = arrayOf("org"), factory = ::Organization)
	var telephones: List<Telephone>? by VCardElementsList(path = arrayOf("tel"), factory = ::Telephone)
	var role: String? by VCardElement(path = arrayOf("role", "text"), factory = Element::value)
	var photos: List<Photo>? by VCardElementsList(path = arrayOf("photo"), factory = Photo.Companion::create)
}

class VCardElementsList<T>(
	val constPath: Array<String> = emptyArray(), val path: Array<String>, val factory: (Element) -> T
) : ReadWriteProperty<VCardStruct, List<T>?> {

	override fun getValue(thisRef: VCardStruct, property: KProperty<*>): List<T>? {
		val root = if (constPath.isEmpty()) thisRef.element else thisRef.element.find(constPath, true)!!

		val result = mutableListOf<T>()

		val cl = root.getChildren(path[0])
		cl.forEach { rt ->
			rt.find(path.copyOfRange(1, path.size))?.let { fnd ->
				val x = factory.invoke(fnd)
				result.add(x)
			}
		}
		return result
	}

	override fun setValue(thisRef: VCardStruct, property: KProperty<*>, value: List<T>?) {
		val root = if (constPath.isEmpty()) thisRef.element else thisRef.element.find(constPath, true)!!

		root.children.filter { it.name == path[0] }.toList().forEach {
			root.remove(it)
		}

		if (value == null) {
			root.parent?.remove(root)
			return
		}

		value.forEach { v -> insertValue(v, path, root) }
	}
}

private fun <T> insertValue(v: T, path: Array<String>, element: Element) {
	if (v is Int) {
		val c = element.crt(path)
		c.value = v.toString()
	} else if (v is String) {
		val c = element.crt(path)
		c.value = v
	} else if (v is VCardStruct) {
		val c = element.crt(path.copyOfRange(0, path.size - 1))
		c.add(v.element)
	} else throw RuntimeException("Unsupported type")
}

private fun Element.crt(path: Array<String>): Element {
	var c: Element? = this
	path.forEach {
		val tmp = element(it) {}
		c?.add(tmp)
		c = tmp
	}
	return c!!
}

private fun Element.find(path: Array<String>, create: Boolean = false): Element? {
	var c: Element? = this
	path.forEach {
		var tmp = c?.getFirstChild(it)
		if (create && tmp == null && c != null) {
			tmp = element(it) {}
			c?.add(tmp)
		}
		c = tmp
	}
	return c
}

open class VCardElement<T>(
	val constPath: Array<String> = emptyArray(), val path: Array<String>, val factory: (Element) -> T
) : ReadWriteProperty<VCardStruct, T?> {

	override fun getValue(thisRef: VCardStruct, property: KProperty<*>): T? {
		return thisRef.element.find(constPath + path)?.let {
			factory.invoke(it)
		}
	}

	override fun setValue(thisRef: VCardStruct, property: KProperty<*>, value: T?) {
		val root = if (constPath.isEmpty()) thisRef.element else thisRef.element.find(constPath, true)!!
		root.children.filter { it.name == path[0] }.toList().forEach {
			root.remove(it)
		}
		if (value != null) insertValue(value, path, root)
	}

}


