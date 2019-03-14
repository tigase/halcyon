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

class Element(val name: String) {

	companion object {
		fun create(name: String, xmlns: String? = null, value: String? = null): Element = Element(name).also {
			if (xmlns != null) it._attributes["xmlns"] = xmlns
			if (value != null) it.value = value
		}
	}

	var parent: Element? = null
		internal set

	internal var _children: MutableList<Element> = ArrayList()// createElementChildrenList()

	internal val _attributes: MutableMap<String, String> = HashMap()// createElementAttributesMap()

	val children: List<Element>
		get() = _children

	val attributes: Map<String, String>
		get() = _attributes

	val xmlns: String?
		get() = _attributes["xmlns"]

	var value: String? = null
		internal set

	fun findChild(vararg elemPath: String): Element? {
		var child: Element? = this
		if (elemPath.isEmpty()) return null
		if (elemPath[0] != name) return null
		// we must start with 1 not 0 as 0 is name of parent element
		var i = 1
		while (i < elemPath.size && child != null) {
			val str = elemPath[i]

			child = child.getFirstChild(str)
			i++
		}
		return child
	}

	fun getFirstChild(): Element? {
		return if (!_children.isEmpty()) {
			_children.first()
		} else {
			null
		}
	}

	fun getFirstChild(name: String): Element? {
		return if (!_children.isEmpty()) {
			_children.firstOrNull { element -> element.name == name }
		} else {
			null
		}
	}

	fun getChildAfter(child: Element): Element? {
		val index = _children.indexOf(child)
		if (index == -1) {
			throw XmlException("Element not part of tree")
		}
		return children[index + 1]
	}

	fun getChildren(name: String): List<Element> = children.filter { element -> element.name == name }

	fun getChildrenNS(xmlns: String): List<Element> = children.filter { element -> element.xmlns == xmlns }

	fun getChildrenNS(name: String, xmlns: String): Element? = children.firstOrNull { element ->
		element.name == name && element.xmlns == xmlns
	}

	fun getNextSibling(): Element? = parent?.getChildAfter(this)

	fun getAsString(): String {
		val builder = StringBuilder()
		builder.append('<')
		builder.append(name)
		if (xmlns != null && (parent == null || parent?.xmlns == null || !parent?.xmlns.equals(xmlns))) {
			builder.append(' ')
			builder.append("xmlns=\"")
			builder.append(EscapeUtils.escape(xmlns))
			builder.append('"')
		}

		for ((key, value1) in attributes) {
			if (key == "xmlns") continue
			builder.append(' ')
			builder.append(key)
			builder.append("=\"")
			builder.append(EscapeUtils.escape(value1))
			builder.append('"')
		}
		if (children.isEmpty() && value == null) {
			builder.append('/')
		}
		builder.append('>')
		for (element in children) {
			builder.append(element.getAsString())
		}
		if (value != null) {
			builder.append(EscapeUtils.escape(value))
		}
		if (!(children.isEmpty() && value == null)) {
			builder.append("</")
			builder.append(name)
			builder.append('>')
		}
		return builder.toString()
	}

	override fun toString(): String {
		return "XMLElement[name='$name' hash='${hashCode()}']"
	}
}