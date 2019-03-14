package org.tigase.jaxmpp.core.xmpp

import org.tigase.jaxmpp.core.exceptions.JaXMPPException

class XMPPException : JaXMPPException {

	val condition: ErrorCondition

	constructor(condition: ErrorCondition) : super() {
		this.condition = condition
	}

	constructor(condition: ErrorCondition, message: String) : super(message) {
		this.condition = condition
	}

	constructor(condition: ErrorCondition, message: String, cause: Throwable) : super(message, cause) {
		this.condition = condition
	}

	constructor(condition: ErrorCondition, cause: Throwable) : super(cause) {
		this.condition = condition
	}



	companion object {
		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-stanzas";
	}
}