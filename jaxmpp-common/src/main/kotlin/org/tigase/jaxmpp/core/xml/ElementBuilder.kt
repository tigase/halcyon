package org.tigase.jaxmpp.core.xml

class ElementBuilder private constructor(val element: Element) {

	var currentElement: Element = element
		private set

	val onTop: Boolean
		get() = currentElement.parent == null

	fun child(name: String): ElementBuilder {
		val element = Element(name)
		element.parent = currentElement
		currentElement._children.add(element)
		currentElement = element
		return this
	}

	fun attribute(key: String, value: String): ElementBuilder {
		currentElement._attributes[key] = value
		return this
	}

	fun attributes(attributes: Map<String, String>): ElementBuilder {
		currentElement._attributes.putAll(attributes)
		return this
	}

	fun value(value: String): ElementBuilder {
		currentElement.value = value
		return this
	}

	fun xmlns(xmlns: String): ElementBuilder {
		currentElement._attributes["xmlns"] = xmlns
		return this
	}

	fun up(): ElementBuilder {
		currentElement = currentElement.parent!!
		return this
	}

	companion object {

		fun create(name: String): ElementBuilder {
			return ElementBuilder(Element(name))
		}

		fun create(name: String, xmlns: String): ElementBuilder {
			val element = Element(name)
			element._attributes["xmlns"] = xmlns
			return ElementBuilder(element)
		}
	}

}