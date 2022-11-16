package tigase.halcyon.core.builder

import tigase.halcyon.core.configuration.ConnectionConfig
import tigase.halcyon.core.connector.socket.SocketConnectorConfig

actual fun defaultConnectionConfiguration(accountBuilder: ConfigurationBuilder, defaultDomain: String): ConnectionConfig =
	SocketConnectorConfig(
		hostname = defaultDomain, port = 5222
	)