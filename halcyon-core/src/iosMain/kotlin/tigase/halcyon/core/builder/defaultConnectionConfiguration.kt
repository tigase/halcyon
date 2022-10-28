package tigase.halcyon.core.builder

import tigase.halcyon.core.configuration.Connection
import tigase.halcyon.core.connector.socket.SocketConnectorConfig

actual fun defaultConnectionConfiguration(accountBuilder: ConfigurationBuilder, defaultDomain: String): Connection = SocketConnectorConfig(
	hostname = defaultDomain
	?: throw ConfigurationException("Cannot determine domain name."), port = 5222
)