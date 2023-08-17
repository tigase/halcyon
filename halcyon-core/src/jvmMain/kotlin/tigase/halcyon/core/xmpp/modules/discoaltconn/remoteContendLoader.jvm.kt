package tigase.halcyon.core.xmpp.modules.discoaltconn

import java.net.URL

actual fun loadRemoteContent(url: String, callback: (String) -> Unit) {
	try {
		callback(URL(url).readText())
	} catch (e: Exception) {
		e.printStackTrace()
	}
}