package org.example

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.builder.createHalcyon
import tigase.halcyon.core.xmpp.modules.MessageReceivedEvent
import tigase.halcyon.core.xmpp.toBareJID
import tigase.halcyon.core.xmpp.toJID

fun main() {
	val halcyon = createHalcyon {
		auth {
			userJID = "yourjid@server.com".toBareJID()
			password { "secretpassword" }
		}
	}
	halcyon.connectAndWait()

	halcyon.eventBus.register(MessageReceivedEvent) {
		if (!it.stanza.body.isNullOrEmpty()) {
			halcyon.request.message {
				to = it.fromJID
				body = "Echo: ${it.stanza.body}"
			}.send()
		}
	}
	halcyon.eventBus.register(MessageReceivedEvent) {
		if (it.stanza.body == "/stop") {
			println("Stopped")
			halcyon.disconnect()
		}
	}

	// waiting while client is connected
	while (halcyon.state == AbstractHalcyon.State.Connected) Thread.sleep(1000)
}