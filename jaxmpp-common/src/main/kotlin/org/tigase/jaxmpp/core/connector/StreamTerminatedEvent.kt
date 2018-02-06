package org.tigase.jaxmpp.core.connector

import org.tigase.jaxmpp.core.eventbus.Event

class StreamTerminatedEvent : Event(TYPE) {
	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.StreamTerminatedEvent";
	}
}