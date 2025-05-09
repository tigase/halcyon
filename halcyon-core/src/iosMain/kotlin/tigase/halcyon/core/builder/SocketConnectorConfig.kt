package tigase.halcyon.core.builder

import tigase.halcyon.core.connector.socket.SocketConnectorConfig

@HalcyonConfigDsl
class SocketConnectionBuilder : ConnectionConfigItemBuilder<SocketConnectorConfig> {

    var hostname: String? = null

    var port: Int = 5222

    override fun build(root: ConfigurationBuilder, defaultDomain: String?): SocketConnectorConfig =
        SocketConnectorConfig(
            hostname = hostname,
            domain = defaultDomain ?: throw ConfigurationException("Cannot determine domain name."),
            port = port
        )
}

fun ConfigurationBuilder.socketConnector(init: SocketConnectionBuilder.() -> Unit) {
    val n = SocketConnectionBuilder()
    n.init()
    this.connection = n
}
