package org.tigase.jaxmpp.core.xml.parser

import org.tigase.jaxmpp.core.xml.Element

abstract class StreamParser() {

	private val parser = SimpleParser()

	private val handler = XMPPDomHandler(onStreamClosed = ::onStreamClosed, onNextElement = ::onNextElement,
										 onStreamStarted = ::onStreamStarted, onParseError = ::onParseError)

	fun parse(data: String) {
		parser.parse(handler, data)
	}

	abstract fun onNextElement(element: Element)
	abstract fun onStreamClosed()
	abstract fun onStreamStarted(attrs: Map<String, String>)
	abstract fun onParseError(errorMessage: String)

}