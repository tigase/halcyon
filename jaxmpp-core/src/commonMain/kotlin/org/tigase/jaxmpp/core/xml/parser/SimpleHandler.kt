package org.tigase.jaxmpp.core.xml.parser

interface SimpleHandler {

	fun error(errorMessage: String)

	fun startElement(name: String, attr_names: Array<String?>?, attr_values: Array<String?>?)

	fun elementCData(cdata: String)

	fun endElement(name: String): Boolean

	fun otherXML(other: String)

	fun saveParserState(state: Any?)

	fun restoreParserState(): Any?

}