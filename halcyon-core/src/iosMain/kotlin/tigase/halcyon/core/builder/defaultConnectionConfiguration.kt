package tigase.halcyon.core.builder

import tigase.halcyon.core.configuration.ConnectionConfig
import tigase.halcyon.core.connector.socket.SocketConnectorConfig

actual fun defaultConnectionConfiguration(
	accountBuilder: ConfigurationBuilder,
	defaultDomain: String,
): ConnectionConfig = SocketConnectorConfig(
	domain = defaultDomain, hostname = null, port = 5222
)