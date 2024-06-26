package tigase.halcyon.core.configuration

import tigase.halcyon.core.builder.ConfigItemBuilder
import tigase.halcyon.core.builder.ConfigurationBuilder
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.builder.ConfigurationException
import tigase.halcyon.core.xmpp.BareJID

interface JIDPasswordSaslConfig : SaslConfig, UserJIDProvider {

	override val userJID: BareJID

	val passwordCallback: () -> String

	val authcId: String?
}

data class JIDPasswordAuthConfig(
	override val userJID: BareJID,
	override val authcId: String?,
	override val passwordCallback: () -> String,
) : JIDPasswordSaslConfig, DomainProvider, UserJIDProvider {

	override val domain: String
		get() = userJID.domain
}

@HalcyonConfigDsl
class JIDPasswordAuthConfigBuilder : ConfigItemBuilder<JIDPasswordAuthConfig> {

	var userJID: BareJID? = null

	var authenticationName: String? = null

	var passwordCallback: (() -> String)? = null

	fun password(callback: (() -> String)?) {
		this.passwordCallback = callback
	}

	override fun build(root: ConfigurationBuilder): JIDPasswordAuthConfig {
		return JIDPasswordAuthConfig(
			userJID = userJID ?: throw ConfigurationException("User JID not specified."),
			passwordCallback = passwordCallback ?: throw ConfigurationException("Password not specified."),
			authcId = authenticationName
		)
	}

}
