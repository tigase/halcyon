package tigase.halcyon.core.builder

import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.configuration.*
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.modules.HalcyonModule
import tigase.halcyon.core.modules.HalcyonModuleProvider
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
import tigase.halcyon.core.xmpp.modules.fileupload.FileUploadModule
import tigase.halcyon.core.xmpp.modules.mam.MAMModule
import tigase.halcyon.core.xmpp.modules.mix.MIXModule
import tigase.halcyon.core.xmpp.modules.muc.MUCModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.modules.receipts.DeliveryReceiptsModule
import tigase.halcyon.core.xmpp.modules.roster.RosterModule
import tigase.halcyon.core.xmpp.modules.serviceFinder.ServiceFinderModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import tigase.halcyon.core.xmpp.modules.spam.BlockingCommandModule
import tigase.halcyon.core.xmpp.modules.uniqueId.UniqueStableStanzaIdModule
import tigase.halcyon.core.xmpp.modules.vcard.VCardModule

@DslMarker
annotation class HalcyonConfigDsl

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

@HalcyonConfigDsl
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

/**
 * A builder class for constructing a configuration object.
 *
 * @constructor Creates a new `ConfigurationBuilder`.
 */
@HalcyonConfigDsl
class ConfigurationBuilder {

	internal val modulesConfigBuilder = ModulesConfigBuilder()

	var auth: ConfigItemBuilder<out SaslConfig>? = null
		set(value) {
			field = value
		}

	var connection: (ConnectionConfigItemBuilder<out ConnectionConfig>)? = null
		internal set

	var registration: RegistrationBuilder? = null
		private set

	/**
	 * Sets the authentication configuration for the account.
	 */
	fun auth(init: JIDPasswordAuthConfigBuilder.() -> Unit) {
		val n = JIDPasswordAuthConfigBuilder()
		n.init()
		this.auth = n
	}

	/**
	 * Registers a new configuration for the account registration process.
	 */
	fun register(init: RegistrationBuilder.() -> Unit) {
		val n = RegistrationBuilder()
		n.init()
		this.registration = n
	}

	@Deprecated("Will be removed soon.")
	fun modules(init: ModulesConfigBuilder.() -> Unit) {
		this.modulesConfigBuilder.init()
	}

	/**
	 * Installs a module with an optional configuration function.
	 * @param provider The module provider to install.
	 * @param configuration The configuration function to apply to the module.
	 */
	fun <M : HalcyonModule, B : Any> install(
		provider: HalcyonModuleProvider<M, B>,
		configuration: B.() -> Unit = {},
	) = this.modulesConfigBuilder.install(provider, configuration)

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

expect fun defaultConnectionConfiguration(accountBuilder: ConfigurationBuilder, defaultDomain: String): ConnectionConfig

fun createConfiguration(
	initializeModules: Boolean = true,
	init: ConfigurationBuilder.() -> Unit,
): ConfigurationBuilder {
	val n = ConfigurationBuilder()
	if (initializeModules) n.installAllModules() else n.installRequiredModules()
	n.init()
	return n
}

fun createHalcyon(installAllModules: Boolean = true, init: ConfigurationBuilder.() -> Unit): Halcyon {
	val n = ConfigurationBuilder()
	if (installAllModules) n.installAllModules() else n.installRequiredModules()
	n.init()
	return Halcyon(n)
}

fun ConfigurationBuilder.installRequiredModules() {
	this.install(StreamErrorModule)
	this.install(StreamFeaturesModule)
	this.install(BindModule)
	this.install(SASLModule)
}

fun ConfigurationBuilder.installAllModules() {
	this.install(DiscoveryModule)
	this.install(RosterModule)
	this.install(PresenceModule)
	this.install(MIXModule)
	this.install(MAMModule)
	this.install(PubSubModule)
	this.install(MessageCarbonsModule)
	this.install(MessageModule)
// temporarly disabled	this.install(StreamManagementModule)
	this.install(SASLModule)
	this.install(BindModule)
	this.install(PingModule)
	this.install(StreamErrorModule)
	this.install(StreamFeaturesModule)
	this.install(EntityCapabilitiesModule)
	this.install(UserAvatarModule)
	this.install(VCardModule)
	this.install(DeliveryReceiptsModule)
	this.install(ChatStateModule)
	this.install(ChatMarkersModule)
	this.install(UniqueStableStanzaIdModule)
	this.install(CommandsModule)
	this.install(BlockingCommandModule)
	this.install(MUCModule)
	this.install(SASL2Module)
	this.install(InBandRegistrationModule)
	this.install(FileUploadModule)
	this.install(ServiceFinderModule)
}