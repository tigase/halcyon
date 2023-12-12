package tigase.halcyon.tests

import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.builder.DefaultTLSProcessorFactory
import tigase.halcyon.core.builder.socketConnector
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SentXMLElementEvent
import tigase.halcyon.core.connector.socket.BouncyCastleTLSProcessor
import tigase.halcyon.core.connector.socket.DefaultTLSProcessor
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.toBareJID
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.util.*

fun loadProperties() = Properties().let { prop ->
	val file = File("./local.properties")
	if (!file.exists()) {
		throw FileNotFoundException(file.absolutePath)
	}
	FileReader(file).use { prop.load(it) }
	Pair<BareJID, String>(
		prop.getProperty("userJID").toBareJID(), prop.getProperty("password")
	)
}

fun createHalcyon(): Halcyon {
	val (jid, password) = loadProperties()
	return tigase.halcyon.core.builder.
	createHalcyon {
		auth {
			userJID = jid
			password { password }
		}
		socketConnector {
			tlsProcessorFactory = BouncyCastleTLSProcessor
		}
	}.apply {
		eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) {
			println(">> ${it.element.getAsString(showValue = false)}")
		}
		eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) {
			println("<< ${it.element.getAsString(showValue = false)}")
		}
	}
}