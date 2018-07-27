package org.tigase.jaxmpp.core.connector

import org.tigase.jaxmpp.core.eventbus.Event

data class ConnectorStateChangeEvent(val oldState: State, val newState: State) : Event(TYPE) {

	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.ConnectorStateChangeEvent";
	}
}