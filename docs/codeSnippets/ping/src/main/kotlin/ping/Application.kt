package ping

import tigase.halcyon.core.builder.createHalcyon
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.modules.PingModule
import tigase.halcyon.core.xmpp.toBareJID
import tigase.halcyon.core.xmpp.toJID
import java.io.FileReader
import java.util.*

fun main() {
	val (jid, password) = Properties().let { prop ->
		FileReader("../local.properties").use { prop.load(it) }
		Pair<BareJID, String>(
			prop.getProperty("userJID")
				.toBareJID(), prop.getProperty("password")
		)
	}
	val halcyon = createHalcyon {
		auth {
			userJID = jid
			password { password }
		}
	}
	halcyon.connectAndWait()

	halcyon.getModule(PingModule)
		.ping("tigase.org".toJID())
		.response { result ->
			result.onSuccess { pong -> println("Pong: ${pong.time}ms") }
			result.onFailure { error -> println("Error $error") }
		}
		.send()

	halcyon.waitForAllResponses()
	halcyon.disconnect()
}