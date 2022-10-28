package tigase.halcyon.core.builder

import tigase.halcyon.core.connector.WebSocketConnectorConfig

@ConfigurationDSLMarker
class WebSocketConnectionBuilder : ConfigItemBuilder<WebSocketConnectorConfig> {

	var domain: String? = null

	var webSocketUrl: String? = null

	override fun build(root: ConfigurationBuilder): WebSocketConnectorConfig {
		val d = domain ?: root.auth?.userJID?.domain ?: root.registration?.domain
		?: throw ConfigurationException("Cannot determine domain name.")
		return WebSocketConnectorConfig(
			webSocketUrl = webSocketUrl ?: "ws://$d:5290/"
		)
	}
}

fun ConfigurationBuilder.webSocketConnector(init: WebSocketConnectionBuilder.() -> Unit) {
	val n = WebSocketConnectionBuilder()
	n.init()
	this.connection = n
}