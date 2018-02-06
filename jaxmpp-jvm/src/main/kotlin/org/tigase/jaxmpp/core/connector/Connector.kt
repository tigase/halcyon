package org.tigase.jaxmpp.core.connector

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.xml.Element

actual class Connector actual constructor(context: Context) : AbstractConnector(context) {

	actual fun start() {
		state = State.Connecting



	}

	actual fun stop() {
		state = State.Disconnecting
	}

	actual fun keepAlive() {}
	actual fun restartStream() {}
	actual fun send(element: Element) {}




}