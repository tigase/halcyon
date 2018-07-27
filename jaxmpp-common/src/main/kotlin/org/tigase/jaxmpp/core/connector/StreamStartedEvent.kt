package org.tigase.jaxmpp.core.connector

import org.tigase.jaxmpp.core.eventbus.Event

data class StreamStartedEvent(val attrs: Map<String, String>) : Event(TYPE) {
	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.StreamStartedEvent";
	}
}