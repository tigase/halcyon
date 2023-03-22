package capabilities

import tigase.halcyon.core.builder.createHalcyon
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule
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
		install(EntityCapabilitiesModule) {
			node = "http://mycompany.com/bestclientever"
		}
	}
	halcyon.connectAndWait()

	// We have to slow down application, because it needs time to retrieve discover information about server.
	Thread.sleep(1000)

	val caps = halcyon.getModule(EntityCapabilitiesModule)
		.getServerCapabilities()
	println(caps)


	halcyon.waitForAllResponses()
	halcyon.disconnect()
}