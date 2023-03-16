package simpleclient

import tigase.halcyon.core.builder.createHalcyon
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.toBareJID
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
		connection
	}
	halcyon.connectAndWait()
	halcyon.disconnect()
}