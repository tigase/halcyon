package tigase.halcyon.core.builder

import tigase.halcyon.core.configuration.ConnectionConfig
import tigase.halcyon.core.connector.WebSocketConnectorConfig

actual fun defaultConnectionConfiguration(accountBuilder: ConfigurationBuilder, defaultDomain: String): ConnectionConfig {
	val d = defaultDomain
	return WebSocketConnectorConfig(
		webSocketUrl = "ws://$d:5290/"
	)
}