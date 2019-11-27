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

interface Element {
	var parent: Element?
	val xmlns: String?
	val name: String
	var value: String?
	fun getFirstChild(): Element?
	fun getFirstChild(name: String): Element?
	fun getChildren(name: String): List<Element>
	fun getChildrenNS(xmlns: String): List<Element>
	fun getChildrenNS(name: String, xmlns: String): Element?
	fun getChildAfter(child: Element): Element?
	fun getAsString(): String
	fun findChild(vararg elemPath: String): Element?
	fun getNextSibling(): Element?
	val children: MutableList<Element>
	val attributes: MutableMap<String, String>
}

open class ElementWrapper(val wrappedElement: Element) : Element by wrappedElement

class ElementImpl(override val name: String) : Element {

	override var parent: Element? = null

	override val children: MutableList<Element> = ArrayList()

	override val attributes: MutableMap<String, String> = HashMap()

	override val xmlns: String?
		get() = attributes["xmlns"]

	override var value: String? = null

	override fun findChild(vararg elemPath: String): Element? {
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

	override fun getFirstChild(): Element? {
		return if (!children.isEmpty()) {
			children.first()
		} else {
			null
		}
	}

	override fun getFirstChild(name: String): Element? {
		return if (!children.isEmpty()) {
			children.firstOrNull { element -> element.name == name }
		} else {
			null
		}
	}

	override fun getChildAfter(child: Element): Element? {
		val index = children.indexOf(child)
		if (index == -1) {
			throw XmlException("Element not part of tree")
		}
		return children[index + 1]
	}

	override fun getChildren(name: String): List<Element> = children.filter { element -> element.name == name }

	override fun getChildrenNS(xmlns: String): List<Element> = children.filter { element -> element.xmlns == xmlns }

	override fun getChildrenNS(name: String, xmlns: String): Element? = children.firstOrNull { element ->
		element.name == name && element.xmlns == xmlns
	}

	override fun getNextSibling(): Element? = parent?.getChildAfter(this)

	override fun getAsString(): String {
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