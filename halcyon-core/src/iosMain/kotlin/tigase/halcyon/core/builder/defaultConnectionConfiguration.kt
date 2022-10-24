package tigase.halcyon.core.builder

import tigase.halcyon.core.configuration.Connection
import tigase.halcyon.core.connector.socket.SocketConnectorConfig

actual fun defaultConnectionConfiguration(accountBuilder: ConfigurationBuilder): Connection = SocketConnectorConfig(
	hostname = accountBuilder.account?.userJID?.domain ?: accountBuilder.registration?.domain
	?: throw ConfigurationException("Cannot determine domain name."), port = 5222
)