package org.tigase.jaxmpp.core.xml

import org.tigase.jaxmpp.core.xmpp.JID

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
//		set(value) {
//			if (value == null) {
//				_attributes.remove("xmlns")
//			} else {
//				_attributes["xmlns"] = value
//			}
//		}
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
}