package tigase.halcyon.core.builder

import tigase.halcyon.core.connector.socket.SocketConnectorConfig

@ConfigurationDSLMarker
class SocketConnectionBuilder : ConnectionConfigItemBuilder<SocketConnectorConfig> {

	var hostname: String? = null

	var port: Int = 5222

	override fun build(root: ConfigurationBuilder, defaultDomain: String?): SocketConnectorConfig {
		val d = hostname ?: defaultDomain ?: throw ConfigurationException("Cannot determine domain name.")
		return SocketConnectorConfig(hostname = d, port = port)
	}
}

fun ConfigurationBuilder.socketConnector(init: SocketConnectionBuilder.() -> Unit) {
	val n = SocketConnectionBuilder()
	n.init()
	this.connection = n
}