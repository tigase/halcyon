package org.tigase.jaxmpp.core.connector

import org.tigase.jaxmpp.core.eventbus.Event
import org.tigase.jaxmpp.core.requests.Request
import org.tigase.jaxmpp.core.xml.Element

data class ConnectorStateChangeEvent(val oldState: State, val newState: State) : Event(TYPE) {

	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.ConnectorStateChangeEvent";
	}
}

data class ReceivedXMLElementEvent(val element: Element) : Event(TYPE) {

	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.ReceivedXMLElementEvent";
	}
}

data class StreamStartedEvent(val attrs: Map<String, String>) : Event(TYPE) {
	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.StreamStartedEvent";
	}
}

class StreamTerminatedEvent : Event(TYPE) {
	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.StreamTerminatedEvent";
	}
}

data class ParseErrorEvent(val errorMessage: String) : Event(TYPE) {
	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.ParseErrorEvent";
	}
}

data class SentXMLElementEvent(val element: Element, val request: Request?) : Event(TYPE) {

	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.SentXMLElementEvent";
	}
}