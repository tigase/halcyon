package tigase.halcyon.core.builder

import tigase.halcyon.core.configuration.Connection
import tigase.halcyon.core.connector.WebSocketConnectorConfig

actual fun defaultConnectionConfiguration(accountBuilder: ConfigurationBuilder, defaultDomain: String): Connection {
	val d = defaultDomain ?: throw ConfigurationException("Cannot determine domain name.")
	return WebSocketConnectorConfig(
		webSocketUrl = "ws://$d:5290/"
	)
}