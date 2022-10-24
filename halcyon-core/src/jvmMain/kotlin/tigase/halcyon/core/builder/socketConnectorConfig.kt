package tigase.halcyon.core.builder

import tigase.halcyon.core.connector.socket.SocketConnectorConfig
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

@ConfigurationDSLMarker
class SocketConnectionBuilder : ConfigItemBuilder<SocketConnectorConfig> {

	var hostname: String? = null

	var port: Int = 5222

	var trustManager: X509TrustManager? = null

	override fun build(root: ConfigurationBuilder): SocketConnectorConfig {
		return SocketConnectorConfig(hostname = hostname ?: root.account?.userJID?.domain ?: root.registration?.domain
		?: throw ConfigurationException("Cannot determine domain name."),
									 port = port,
									 trustManager = trustManager ?: object : X509TrustManager {
										 override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
										 }

										 override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
										 }

										 override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
									 })
	}
}

fun ConfigurationBuilder.socketConnector(init: SocketConnectionBuilder.() -> Unit) {
	val n = SocketConnectionBuilder()
	n.init()
	this.connection = n
}