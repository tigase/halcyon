package discovery

import tigase.halcyon.core.builder.createHalcyon
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.toBareJID
import tigase.halcyon.core.xmpp.toJID
import java.io.FileReader
import java.util.*

fun main() {
	val (jid, password) = Properties().let { prop ->
		FileReader("./local.properties").use { prop.load(it) }
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
		install(DiscoveryModule) {
			clientCategory = "client"
			clientType = "console"
			clientName = "Code Snippet Demo"
			clientVersion = "1.2.3"
		}
	}
	halcyon.connectAndWait()

	halcyon.getModule(DiscoveryModule)
		.info("tigase.org".toJID())
		.response { result ->
			result.onFailure { error -> println("Error $error") }
			result.onSuccess { info ->
				println("Received info from ${info.jid}:")
				println("Features " + info.features)
				println(info.identities.joinToString { identity ->
					"${identity.name} (${identity.category}, ${identity.type})"
				})
			}
		}
		.send()

	halcyon.getModule(DiscoveryModule)
		.items("tigase.org".toJID())
		.response { result ->
			result.onFailure { error -> println("Error $error") }
			result.onSuccess { items ->
				println("Received info from ${items.jid}:")
				println(items.items.joinToString { "${it.name} (${it.jid}, ${it.node})" })
			}
		}
		.send()

	halcyon.waitForAllResponses()
	halcyon.disconnect()
}