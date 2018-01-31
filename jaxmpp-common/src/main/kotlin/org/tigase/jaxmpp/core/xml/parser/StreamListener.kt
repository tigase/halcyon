package org.tigase.jaxmpp.core.xml.parser

import org.tigase.jaxmpp.core.xml.Element

interface StreamListener {

	fun onNextElement(element: Element)

	fun onStreamClose()

	fun onStreamOpened(attrs: Map<String, String>)

}