package org.tigase.jaxmpp.core.xml.parser

import org.tigase.jaxmpp.core.xml.Element

expect abstract class StreamParser() {

	fun parse(data: String)

	abstract fun onNextElement(element: Element)

	abstract fun onParseError(errorMessage: String)

	abstract fun onStreamClosed()

	abstract fun onStreamStarted(attrs: Map<String, String>)

}