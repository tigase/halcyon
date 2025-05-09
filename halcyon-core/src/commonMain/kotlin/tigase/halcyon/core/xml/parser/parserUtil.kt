package tigase.halcyon.core.xml.parser

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.XmlException

fun parseXML(data: String): Element {
    var r: Element? = null
    var e: Throwable? = null
    val parser = object : StreamParser() {
        override fun onParseError(errorMessage: String) {
            e = XmlException(errorMessage)
        }

        override fun onNextElement(element: Element) {
            r = element
        }

        override fun onStreamClosed() {
            e = XmlException("Unexpected stream close")
        }

        override fun onStreamStarted(attrs: Map<String, String>) {
            e = XmlException("Unexpected stream start")
        }
    }

    parser.parse(data)

    if (e != null) throw e!!

    return r ?: throw XmlException("No data")
}

fun parseXMLOrNull(data: String): Element? = try {
    parseXML(data)
} catch (e: Exception) {
    null
}
