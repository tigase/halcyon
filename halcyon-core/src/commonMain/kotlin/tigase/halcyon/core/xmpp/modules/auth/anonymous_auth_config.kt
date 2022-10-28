package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.builder.ConfigItemBuilder
import tigase.halcyon.core.builder.ConfigurationBuilder
import tigase.halcyon.core.builder.ConfigurationDSLMarker
import tigase.halcyon.core.builder.ConfigurationException
import tigase.halcyon.core.configuration.DomainProvider
import tigase.halcyon.core.configuration.SaslConfig

data class AnonymousSaslConfig(override val domain: String) : SaslConfig, DomainProvider

@ConfigurationDSLMarker
class AnonymousSaslConfigBuilder : ConfigItemBuilder<AnonymousSaslConfig> {

	var domain: String? = null

	override fun build(root: ConfigurationBuilder): AnonymousSaslConfig =
		AnonymousSaslConfig(domain = this.domain ?: throw ConfigurationException("Domain is not specified."))
}

fun ConfigurationBuilder.authAnonymous(init: AnonymousSaslConfigBuilder.() -> Unit) {
	val n = AnonymousSaslConfigBuilder()
	n.init()
	this.auth = n
}