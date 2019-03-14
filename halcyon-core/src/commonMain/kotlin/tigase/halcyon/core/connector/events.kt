package tigase.halcyon.core.connector

import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.xml.Element

data class ConnectorStateChangeEvent(
	val oldState: tigase.halcyon.core.connector.State,
	val newState: tigase.halcyon.core.connector.State
) : tigase.halcyon.core.eventbus.Event(
	tigase.halcyon.core.connector.ConnectorStateChangeEvent.Companion.TYPE
) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.ConnectorStateChangeEvent"
	}
}

data class ReceivedXMLElementEvent(val element: Element) :
	tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.connector.ReceivedXMLElementEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.ReceivedXMLElementEvent"
	}
}

data class StreamStartedEvent(val attrs: Map<String, String>) :
	tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.connector.StreamStartedEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.StreamStartedEvent"
	}
}

class StreamTerminatedEvent :
	tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.connector.StreamTerminatedEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.StreamTerminatedEvent"
	}
}

data class ParseErrorEvent(val errorMessage: String) :
	tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.connector.ParseErrorEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.ParseErrorEvent"
	}
}

data class SentXMLElementEvent(val element: Element, val request: Request?) :
	tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.connector.SentXMLElementEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.SentXMLElementEvent"
	}
}