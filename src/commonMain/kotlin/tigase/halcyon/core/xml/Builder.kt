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
package tigase.halcyon.core.xml

class ElementAttributes(private val element: Element) {

	operator fun set(name: String, value: String?) {
		if (value == null) element.attributes.remove(name)
		else element.attributes[name] = value
	}

	operator fun get(name: String): String? {
		return element.attributes[name]
	}
}

open class ElementNode(internal val element: Element) {

	fun attribute(name: String, value: String) {
		element.attributes[name] = value
	}

	val attributes = ElementAttributes(element)

	var xmlns: String?
		set(value) {
			attributes["xmlns"] = value
		}
		get() = element.xmlns

	operator fun String.unaryPlus() {
		value = if (value == null) this else value + this
	}

	var value: String?
		set(value) {
			element.value = value
		}
		get() = element.value

	/**
	 * To tkjfjkshdfjk
	 */
	operator fun String.invoke(value: String): Element {
		val n = element(this)
		n.value = value
		return n
	}

	operator fun String.invoke(
		vararg attributes: Pair<String, Any>, init: (ElementNode.() -> Unit)? = null
	): Element {
		return element(this, init)
	}

	fun addChild(e: Element) {
		e.parent = element
		element.children.add(e)
	}

	fun element(name: String, init: (ElementNode.() -> Unit)? = null): Element {
		val e = ElementNode(ElementImpl(name))
		if (init != null) e.init()
		e.element.parent = element
		element.children.add(e.element)
		return e.element
	}

}

fun element(name: String, init: ElementNode.() -> Unit): Element {
	val n = ElementNode(ElementImpl(name))
	n.init()
	return n.element
}

fun response(element: Element, init: ElementNode.() -> Unit): Element {
	val n = ElementNode(ElementImpl(element.name))
	n.element.attributes["id"] = element.attributes["id"]!!
	n.element.attributes["type"] = "result"
	if (element.attributes["to"] != null) n.element.attributes["from"] = element.attributes["to"]!!
	if (element.attributes["from"] != null) n.element.attributes["to"] = element.attributes["from"]!!
	n.init()
	return n.element
}

