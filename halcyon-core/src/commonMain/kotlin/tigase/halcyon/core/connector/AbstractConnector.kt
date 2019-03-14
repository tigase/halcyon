package tigase.halcyon.core.connector

import tigase.halcyon.core.xmpp.SessionController

abstract class AbstractConnector(val context: tigase.halcyon.core.Context) {

	var state: tigase.halcyon.core.connector.State = tigase.halcyon.core.connector.State.Disconnected
		protected set(value) {
			val old = field
			field = value
			context.eventBus.fire(tigase.halcyon.core.connector.ConnectorStateChangeEvent(old, field))
		}

	abstract fun createSessionController(): SessionController

	abstract fun send(data: CharSequence)

	abstract fun start()

	abstract fun stop()

}