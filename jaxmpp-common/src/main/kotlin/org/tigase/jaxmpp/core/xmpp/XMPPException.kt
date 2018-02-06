package org.tigase.jaxmpp.core.xmpp

import org.tigase.jaxmpp.core.exceptions.JaXMPPException

class XMPPException : JaXMPPException {
	constructor() : super()
	constructor(message: String?) : super(message)
	constructor(message: String?, cause: Throwable?) : super(message, cause)
	constructor(cause: Throwable?) : super(cause)

	companion object {
		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-stanzas";
	}
}