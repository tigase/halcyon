package tigase.halcyon.tests

import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SentXMLElementEvent
import tigase.halcyon.core.xmpp.toBareJID

fun createHalcyon(): Halcyon {
	return Halcyon().apply {
		configure {
			userJID = "admin@sailboat.local".toBareJID()
			password = "admin"
			domain = "sailboat.local"
			socketConnector { }
		}
		eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) { println(">> ${it.element.getAsString()}") }
		eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) { println("<< ${it.element.getAsString()}") }
	}
}