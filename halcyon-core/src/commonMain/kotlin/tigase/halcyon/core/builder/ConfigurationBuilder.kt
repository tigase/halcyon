package tigase.halcyon.core.builder

import tigase.halcyon.core.Context
import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.configuration.*
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.modules.ModulesManager
import tigase.halcyon.core.xmpp.forms.JabberDataForm

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

	internal val modulesConfigBuilders = mutableListOf<(ModulesConfigBuilder.() -> Unit)>()

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
		this.modulesConfigBuilders += init
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

fun createConfiguration(init: ConfigurationBuilder.() -> Unit): ConfigurationBuilder {
	val n = ConfigurationBuilder()
	n.init()
	return n
}

fun createHalcyon(init: ConfigurationBuilder.() -> Unit): Halcyon {
	val n = ConfigurationBuilder()
	n.init()
	return Halcyon(n)
}