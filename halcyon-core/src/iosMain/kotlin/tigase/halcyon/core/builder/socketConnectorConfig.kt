package tigase.halcyon.core.builder

import tigase.halcyon.core.connector.socket.SocketConnectorConfig

@ConfigurationDSLMarker
class SocketConnectionBuilder : ConfigItemBuilder<SocketConnectorConfig> {

	var hostname: String? = null

	var port: Int = 5222

	override fun build(root: ConfigurationBuilder): SocketConnectorConfig {
		val d = hostname ?: root.account?.userJID?.domain ?: root.registration?.domain
		?: throw ConfigurationException("Cannot determine domain name.")
		return SocketConnectorConfig(hostname = d, port = port)
	}
}

fun ConfigurationBuilder.socketConnector(init: SocketConnectionBuilder.() -> Unit) {
	val n = SocketConnectionBuilder()
	n.init()
	this.connection = n
}