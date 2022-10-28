package tigase.halcyon.core.builder

import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.configuration.*
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.modules.*
import tigase.halcyon.core.xmpp.modules.auth.SASL2Module
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.avatar.UserAvatarModule
import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule
import tigase.halcyon.core.xmpp.modules.carbons.MessageCarbonsModule
import tigase.halcyon.core.xmpp.modules.chatmarkers.ChatMarkersModule
import tigase.halcyon.core.xmpp.modules.chatstates.ChatStateModule
import tigase.halcyon.core.xmpp.modules.commands.CommandsModule
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.modules.mam.MAMModule
import tigase.halcyon.core.xmpp.modules.mix.MIXModule
import tigase.halcyon.core.xmpp.modules.muc.MUCModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.modules.receipts.DeliveryReceiptsModule
import tigase.halcyon.core.xmpp.modules.roster.RosterModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import tigase.halcyon.core.xmpp.modules.spam.BlockingCommandModule
import tigase.halcyon.core.xmpp.modules.uniqueId.UniqueStableStanzaIdModule
import tigase.halcyon.core.xmpp.modules.vcard.VCardModule

@DslMarker
annotation class ConfigurationDSLMarker

interface ConfigItemBuilder<T> {

	fun build(root: ConfigurationBuilder): T

}

interface ConnectionConfigItemBuilder<T> {

	fun build(root: ConfigurationBuilder, defaultDomain: String?): T

}

class ConfigurationException : HalcyonException {

	constructor() : super()
	constructor(message: String?) : super(message)
	constructor(message: String?, cause: Throwable?) : super(message, cause)
	constructor(cause: Throwable?) : super(cause)
}

@ConfigurationDSLMarker
class RegistrationBuilder : ConfigItemBuilder<Registration> {

	var domain: String? = null

	private var formHandler: ((JabberDataForm) -> Unit)? = null

	private var formHandlerWithResponse: ((JabberDataForm) -> JabberDataForm)? = null

	fun registrationFormHandler(handler: (JabberDataForm) -> Unit) {
		this.formHandler = handler
	}

	fun registrationHandler(handler: (JabberDataForm) -> JabberDataForm) {
		this.formHandlerWithResponse = handler
	}

	override fun build(root: ConfigurationBuilder): Registration {
		if (formHandler == null && formHandlerWithResponse == null) throw ConfigurationException("At least one registration form handler must be declared.")
		return Registration(
			domain = domain ?: throw ConfigurationException("Domain not specified."),
			formHandler = formHandler,
			formHandlerWithResponse = formHandlerWithResponse
		)
	}

}

@ConfigurationDSLMarker
class ConfigurationBuilder {

	internal val modulesConfigBuilder = ModulesConfigBuilder()

	var auth: ConfigItemBuilder<out SaslConfig>? = null
		set(value) {
			if (field != null) throw ConfigurationException("Authentication is configured already.")
			field = value
		}

	var connection: (ConnectionConfigItemBuilder<out Connection>)? = null
		internal set

	var registration: RegistrationBuilder? = null
		private set

	fun auth(init: JIDPasswordAuthConfigBuilder.() -> Unit) {
		val n = JIDPasswordAuthConfigBuilder()
		n.init()
		this.auth = n
	}

	fun register(init: RegistrationBuilder.() -> Unit) {
		val n = RegistrationBuilder()
		n.init()
		this.registration = n
	}

	fun modules(init: ModulesConfigBuilder.() -> Unit) {
		this.modulesConfigBuilder.init()
	}

	fun build(): Configuration {
		val account = this.auth?.build(this)
		val registration = this.registration?.build(this)
		if (account == null && registration == null) throw ConfigurationException("Account or account creation details must be provided")

		val domain = if (account is DomainProvider) {
			account.domain
		} else registration?.domain ?: throw ConfigurationException("Cannot determine domain.")
		val connection = connection?.build(this, domain) ?: defaultConnectionConfiguration(this, domain)

		return Configuration(
			sasl = account,
			registration = registration,
			connection = connection,
		)
	}

}

expect fun defaultConnectionConfiguration(accountBuilder: ConfigurationBuilder, defaultDomain: String): Connection

fun createConfiguration(
	initializeModules: Boolean = true,
	init: ConfigurationBuilder.() -> Unit,
): ConfigurationBuilder {
	val n = ConfigurationBuilder()
	if (initializeModules) n.initiateAllModules()
	n.init()
	return n
}

fun createHalcyon(initializeModules: Boolean = true, init: ConfigurationBuilder.() -> Unit): Halcyon {
	val n = ConfigurationBuilder()
	if (initializeModules) n.initiateAllModules()
	n.init()
	return Halcyon(n)
}

fun ConfigurationBuilder.initiateRequiredModules() {
	this.modulesConfigBuilder.install(StreamErrorModule)
	this.modulesConfigBuilder.install(StreamFeaturesModule)
	this.modulesConfigBuilder.install(BindModule)
	this.modulesConfigBuilder.install(SASLModule)
}

fun ConfigurationBuilder.initiateAllModules() {
	this.modulesConfigBuilder.install(DiscoveryModule)
	this.modulesConfigBuilder.install(RosterModule)
	this.modulesConfigBuilder.install(PresenceModule)
	this.modulesConfigBuilder.install(MIXModule)
	this.modulesConfigBuilder.install(MAMModule)
	this.modulesConfigBuilder.install(PubSubModule)
	this.modulesConfigBuilder.install(MessageCarbonsModule)
	this.modulesConfigBuilder.install(MessageModule)
	this.modulesConfigBuilder.install(StreamManagementModule)
	this.modulesConfigBuilder.install(SASLModule)
	this.modulesConfigBuilder.install(BindModule)
	this.modulesConfigBuilder.install(PingModule)
	this.modulesConfigBuilder.install(StreamErrorModule)
	this.modulesConfigBuilder.install(StreamFeaturesModule)
	this.modulesConfigBuilder.install(EntityCapabilitiesModule)
	this.modulesConfigBuilder.install(UserAvatarModule)
	this.modulesConfigBuilder.install(VCardModule)
	this.modulesConfigBuilder.install(DeliveryReceiptsModule)
	this.modulesConfigBuilder.install(ChatStateModule)
	this.modulesConfigBuilder.install(ChatMarkersModule)
	this.modulesConfigBuilder.install(UniqueStableStanzaIdModule)
	this.modulesConfigBuilder.install(CommandsModule)
	this.modulesConfigBuilder.install(BlockingCommandModule)
	this.modulesConfigBuilder.install(MUCModule)
	this.modulesConfigBuilder.install(SASL2Module)
	this.modulesConfigBuilder.install(InBandRegistrationModule)
}