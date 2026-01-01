package tigase.halcyon.core.builder

import tigase.halcyon.core.configuration.ConnectionConfig
import tigase.halcyon.core.connector.WebSocketConnectorConfig

actual fun defaultConnectionConfiguration(
	accountBuilder: ConfigurationBuilder,
	defaultDomain: String,
): ConnectionConfig {
	return WebSocketConnectorConfig(domain = defaultDomain, webSocketUrl = null, allowUnsecureConnection = false)
}