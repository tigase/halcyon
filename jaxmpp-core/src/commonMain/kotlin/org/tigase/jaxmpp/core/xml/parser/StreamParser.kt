package org.tigase.jaxmpp.core.xml.parser

import org.tigase.jaxmpp.core.xml.Element

abstract class StreamParser() {

	private val parser = SimpleParser()

	private val handler = XMPPDomHandler(onStreamClosed = ::onStreamClosed, onNextElement = ::onNextElement,
										 onStreamStarted = ::onStreamStarted, onParseError = ::onParseError)

	fun parse(data: String) {
		val ca = CharArray(data.length)
		for (i in 0 until data.length) {
			ca[i] = data[i]
		}
		parser.parse(handler, ca)
	}

	fun parse(data: CharArray) {
		parser.parse(handler, data)
	}

	fun parse(data: CharArray, offset: Int, len: Int) {
		parser.parse(handler, data, offset, len)
	}

	abstract fun onNextElement(element: Element)
	abstract fun onStreamClosed()
	abstract fun onStreamStarted(attrs: Map<String, String>)
	abstract fun onParseError(errorMessage: String)

}