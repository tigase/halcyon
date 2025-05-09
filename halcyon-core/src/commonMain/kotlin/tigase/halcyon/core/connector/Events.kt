/*
 * halcyon-core
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

import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.xml.Element

data class ConnectorStateChangeEvent(val oldState: State, val newState: State) : Event(TYPE) {

    companion object : EventDefinition<ConnectorStateChangeEvent> {

        override val TYPE = "tigase.halcyon.core.connector.ConnectorStateChangeEvent"
    }
}

data class ReceivedXMLElementEvent(val element: Element) : Event(TYPE) {

    companion object : EventDefinition<ReceivedXMLElementEvent> {

        override val TYPE = "tigase.halcyon.core.connector.ReceivedXMLElementEvent"
    }
}

data class StreamStartedEvent(val attrs: Map<String, String>) : Event(TYPE) {

    companion object : EventDefinition<StreamStartedEvent> {

        override val TYPE = "tigase.halcyon.core.connector.StreamStartedEvent"
    }
}

class StreamTerminatedEvent : Event(TYPE) {

    companion object : EventDefinition<StreamStartedEvent> {

        override val TYPE = "tigase.halcyon.core.connector.StreamTerminatedEvent"
    }
}

data class ParseErrorEvent(val errorMessage: String) : Event(TYPE) {

    companion object : EventDefinition<ParseErrorEvent> {

        override val TYPE = "tigase.halcyon.core.connector.ParseErrorEvent"
    }
}

data class SentXMLElementEvent(val element: Element, val request: Request<*, *>?) : Event(TYPE) {

    companion object : EventDefinition<SentXMLElementEvent> {

        override val TYPE = "tigase.halcyon.core.connector.SentXMLElementEvent"
    }
}

abstract class ConnectionErrorEvent : Event(TYPE) {

    companion object : EventDefinition<ConnectionErrorEvent> {

        override val TYPE = "tigase.halcyon.core.connector.ConnectionErrorEvent"
    }
}
