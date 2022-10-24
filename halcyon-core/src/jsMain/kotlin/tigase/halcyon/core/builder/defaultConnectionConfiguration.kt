package tigase.halcyon.core.builder

import tigase.halcyon.core.configuration.Connection
import tigase.halcyon.core.connector.WebSocketConnectorConfig

actual fun defaultConnectionConfiguration(accountBuilder: ConfigurationBuilder): Connection {
	val d = accountBuilder.account?.userJID?.domain ?: accountBuilder.registration?.domain
	?: throw ConfigurationException("Cannot determine domain name.")
	return WebSocketConnectorConfig(
		webSocketUrl = "ws://$d:5290/"
	)
}