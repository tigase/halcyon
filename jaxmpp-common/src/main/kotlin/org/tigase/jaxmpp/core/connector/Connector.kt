package org.tigase.jaxmpp.core.connector

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.xml.Element

expect class Connector(context: Context) : AbstractConnector {

	fun start()

	fun stop()

	fun keepAlive()

	fun restartStream()

	fun send(element: Element)
}