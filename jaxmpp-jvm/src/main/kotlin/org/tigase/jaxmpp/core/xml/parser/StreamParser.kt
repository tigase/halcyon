package org.tigase.jaxmpp.core.xml.parser

import org.tigase.jaxmpp.core.xml.Element

actual abstract class StreamParser {

	private val parser = SimpleParser()

	private val handler = XMPPDomHandler(onStreamClosed = ::onStreamClosed, onNextElement = ::onNextElement,
										 onStreamStarted = ::onStreamStarted, onParseError = ::onParseError)

	actual fun parse(data: String) {
		parser.parse(handler, data.toCharArray(), 0, data.length)
	}

	actual abstract fun onNextElement(element: Element)
	actual abstract fun onStreamClosed()
	actual abstract fun onStreamStarted(attrs: Map<String, String>)
	actual abstract fun onParseError(errorMessage: String)

}
