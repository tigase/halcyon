package org.tigase.jaxmpp.core

import org.tigase.jaxmpp.core.connector.AbstractConnector
import org.tigase.jaxmpp.core.connector.socket.SocketConnector

actual class JaXMPP actual constructor() : AbstractJaXMPP() {

	override fun createConnector(): AbstractConnector {
		return SocketConnector(this)
	}

}