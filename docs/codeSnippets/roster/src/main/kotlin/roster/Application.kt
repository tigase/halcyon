package roster

import tigase.halcyon.core.builder.createHalcyon
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.modules.roster.RosterItem
import tigase.halcyon.core.xmpp.modules.roster.RosterModule
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
	}
	halcyon.connectAndWait()

	// Add new contact
	halcyon.getModule(RosterModule)
		.addItem(
			RosterItem(
				jid = "contact@somewhere.com".toBareJID(),
				name = "My friend",
			)
		)
		.send()

	// Update contact
	halcyon.getModule(RosterModule)
		.addItem(
			RosterItem(
				jid = "contact@somewhere.com".toBareJID(),
				name = "My best friend!",
			)
		)
		.send()

	// Update contact
	halcyon.getModule(RosterModule)
		.deleteItem("contact@somewhere.com".toBareJID())
		.send()



	halcyon.waitForAllResponses()
	halcyon.disconnect()
}