package org.tigase.jaxmpp.core.connector

import org.tigase.jaxmpp.core.eventbus.Event

class StateChangeEvent(val oldStade: State, val newState: State) : Event(TYPE) {

	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.StateChangeEvent";
	}
}