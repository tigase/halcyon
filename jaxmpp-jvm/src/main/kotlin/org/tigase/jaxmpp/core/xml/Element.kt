package org.tigase.jaxmpp.core.xml

import java.util.*

internal actual fun createElementAttributesMap(): MutableMap<String, String> {
	return HashMap()
}

internal actual fun createElementChildrenList(): MutableList<Element> {
	return LinkedList()
}