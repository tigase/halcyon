package org.tigase.jaxmpp.core.connector

import org.tigase.jaxmpp.core.eventbus.Event
import org.tigase.jaxmpp.core.xml.Element

data class ReceivedXMLElementEvent(val element: Element) : Event(TYPE) {

	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.connector.ReceivedXMLElementEvent";
	}
}