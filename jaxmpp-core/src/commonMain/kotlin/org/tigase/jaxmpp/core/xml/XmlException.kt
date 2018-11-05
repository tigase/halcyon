package org.tigase.jaxmpp.core.xml

import org.tigase.jaxmpp.core.exceptions.JaXMPPException

class XmlException : JaXMPPException {
	constructor() : super()
	constructor(message: String?) : super(message)
	constructor(message: String?, cause: Throwable?) : super(message, cause)
	constructor(cause: Throwable?) : super(cause)
}