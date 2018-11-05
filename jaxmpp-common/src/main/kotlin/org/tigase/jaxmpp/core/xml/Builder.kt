package org.tigase.jaxmpp.core.xml

import org.tigase.jaxmpp.core.xmpp.IdGenerator

class Node(name: String) {

	internal val element = Element(name)

	fun attribute(name: String, value: String) {
		element._attributes[name] = value
	}

	var xmlns: String?
		set(value) {
			if (value == null) {
				element._attributes.remove("xmlns")
			} else {
				element._attributes["xmlns"] = value
			}
		}
		get() = element.xmlns

	operator fun String.unaryPlus() {
		value = this
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

	operator fun String.invoke(vararg attributes: Pair<String, Any>, init: (Node.() -> Unit)? = null): Element {
		return element(this, init)
	}

	fun id() {
		element._attributes["id"] = IdGenerator.nextId()
	}

	fun element(name: String, init: (Node.() -> Unit)? = null): Element {
		val e = Node(name)
		if (init != null) e.init()
		e.element.parent = element
		element._children.add(e.element)
		return e.element
	}

}

fun stanza(name: String, init: Node.() -> Unit): Element {
	val n = Node(name)
	n.init()
	return n.element
}

fun response(element: Element, init: Node.() -> Unit): Element {
	val n = Node(element.name)
	n.element._attributes["id"] = element._attributes["id"]!!
	n.element._attributes["type"] = "result"
	if (element._attributes["to"] != null) n.element._attributes["from"] = element._attributes["to"]!!
	if (element._attributes["from"] != null) n.element._attributes["to"] = element._attributes["from"]!!
	n.init()
	return n.element
}

fun main(args: Array<String>) {
	val stanza = stanza("message") {
		attribute("id", "1")
		attribute("to", "romeo@example.net")
		attribute("from", "juliet@example.com/balcony")
		attribute("type", "chat")
		"body" {
			+"Wherefore art thou, Romeo?"
		}
		"thread"("e0ffe42b28561960c6b12b944a092794b9683a38")
		element("subject") {
			value = "I implore you!"
		}
		"x" {
			xmlns = "test:urn"
			"presence" {
				+"dnd"
			}
		}
	}

	println(stanza.getAsString())
}