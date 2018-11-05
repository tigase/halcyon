package org.tigase.jaxmpp.core.connector

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.xmpp.SessionController

abstract class AbstractConnector(val context: Context) {

	var state: State = State.Disconnected
		protected set(value) {
			val old = field
			field = value
			context.eventBus.fire(ConnectorStateChangeEvent(old, field))
		}

	abstract fun createSessionController(): SessionController

	abstract fun send(data: CharSequence)

	abstract fun start()

	abstract fun stop()

}