package org.tigase.jaxmpp.core.connector

import org.tigase.jaxmpp.core.Context

abstract class AbstractConnector(val context: Context) {

	var state: State = State.Disconnected
		protected set(value) {
			val old = field
			field = value
			context.eventBus.fire(StateChangeEvent(old, field))
		}

	var isCompressed: Boolean = false
		protected set

	var isSecure: Boolean = false
		protected set

}