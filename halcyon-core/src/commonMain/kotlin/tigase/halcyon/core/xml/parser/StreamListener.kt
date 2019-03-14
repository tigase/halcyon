package tigase.halcyon.core.xml.parser

import tigase.halcyon.core.xml.Element

interface StreamListener {

	fun onNextElement(element: Element)

	fun onStreamClose()

	fun onStreamOpened(attrs: Map<String, String>)

}