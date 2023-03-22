package presence

import tigase.halcyon.core.builder.createHalcyon
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.modules.PingModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.modules.presence.typeAndShow
import tigase.halcyon.core.xmpp.modules.roster.RosterLoadedEvent
import tigase.halcyon.core.xmpp.stanzas.PresenceType
import tigase.halcyon.core.xmpp.stanzas.Show
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

	// setting own presence
	halcyon.getModule(PresenceModule)
		.sendPresence(show = Show.Chat, status = "I'm ready for party!")
		.send()

	// sending direct presence
	halcyon.getModule(PresenceModule)
		.sendPresence(jid = "mom@server.com".toJID(), show = Show.DnD, status = "I'm doing my homework!")
		.send()

	// subscribe presence of buddy@somewhere.com
	halcyon.getModule(PresenceModule)
		.sendSubscriptionSet(jid = "buddy@somewhere.com".toJID(), presenceType = PresenceType.Subscribe)
		.send()

	val contactStatus = halcyon.getModule(PresenceModule)
		.getBestPresenceOf("dad@server.com".toBareJID())
		.typeAndShow()


	halcyon.waitForAllResponses()
	halcyon.disconnect()
}