/*
 * Tigase Halcyon XMPP Library
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core.connector

import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.xml.Element

data class ConnectorStateChangeEvent(
	val oldState: tigase.halcyon.core.connector.State, val newState: tigase.halcyon.core.connector.State
) : tigase.halcyon.core.eventbus.Event(
	tigase.halcyon.core.connector.ConnectorStateChangeEvent.Companion.TYPE
) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.ConnectorStateChangeEvent"
	}
}

data class ReceivedXMLElementEvent(val element: Element) : tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.connector.ReceivedXMLElementEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.ReceivedXMLElementEvent"
	}
}

data class StreamStartedEvent(val attrs: Map<String, String>) : tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.connector.StreamStartedEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.StreamStartedEvent"
	}
}

class StreamTerminatedEvent : tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.connector.StreamTerminatedEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.StreamTerminatedEvent"
	}
}

data class ParseErrorEvent(val errorMessage: String) : tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.connector.ParseErrorEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.ParseErrorEvent"
	}
}

data class SentXMLElementEvent(val element: Element, val request: Request?) : tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.connector.SentXMLElementEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.connector.SentXMLElementEvent"
	}
}