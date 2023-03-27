package tigase.halcyon.core.builder

import tigase.halcyon.core.connector.WebSocketConnectorConfig

@HalcyonConfigDsl
class WebSocketConnectionBuilder : ConnectionConfigItemBuilder<WebSocketConnectorConfig> {

	var webSocketUrl: String? = null

	override fun build(root: ConfigurationBuilder, defaultDomain: String?): WebSocketConnectorConfig {
		return WebSocketConnectorConfig(
			domain = defaultDomain ?: throw ConfigurationException("Cannot determine domain name."),
			webSocketUrl = webSocketUrl ?: "ws://$defaultDomain:5290/"
		)
	}
}

fun ConfigurationBuilder.webSocketConnector(init: WebSocketConnectionBuilder.() -> Unit) {
	val n = WebSocketConnectionBuilder()
	n.init()
	this.connection = n
}