package tigase.halcyon.core.builder

import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.configuration.Account
import tigase.halcyon.core.configuration.Configuration
import tigase.halcyon.core.configuration.Connection
import tigase.halcyon.core.configuration.Registration
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.forms.JabberDataForm

@DslMarker
annotation class ConfigurationDSLMarker

interface ConfigItemBuilder<T> {

	fun build(root: ConfigurationBuilder): T

}

class ConfigurationException : HalcyonException {

	constructor() : super()
	constructor(message: String?) : super(message)
	constructor(message: String?, cause: Throwable?) : super(message, cause)
	constructor(cause: Throwable?) : super(cause)
}

@ConfigurationDSLMarker
class AccountBuilder : ConfigItemBuilder<Account> {

	var userJID: BareJID? = null

	var resource: String? = null

	@Deprecated("Will be removed ASAP")
	var domain: String? = null

	var authzIdJID: BareJID? = null

	var passwordCallback: (() -> String)? = null

	fun password(callback: (() -> String)?) {
		this.passwordCallback = callback
	}

	override fun build(root: ConfigurationBuilder): Account {
		return Account(
			userJID = userJID ?: throw ConfigurationException("User JID not specified."),
			passwordCallback = passwordCallback ?: throw ConfigurationException("Password not specified."),
			resource = resource,
			domain = domain,
			authzIdJID = authzIdJID
		)
	}

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

	var account: AccountBuilder? = null
		private set

	var connection: (ConfigItemBuilder<out Connection>)? = null
		internal set

	var registration: RegistrationBuilder? = null
		private set

	fun account(init: AccountBuilder.() -> Unit) {
		val n = AccountBuilder()
		n.init()
		this.account = n
	}

//	fun connection(init: ConnectionBuilder.() -> Unit) {
//		val n = ConnectionBuilder()
//		n.init()
//		this.connection = n
//	}

	fun createAccount(init: RegistrationBuilder.() -> Unit) {
		val n = RegistrationBuilder()
		n.init()
		this.registration = n
	}

	fun build(): Configuration {
		val account = this.account?.build(this)
		val registration = this.registration?.build(this)
		if (account == null && registration == null) throw ConfigurationException("Account or account creation details must be provided")

		return Configuration(
			account = account,
			registration = registration,
			connection = connection?.build(this) ?: defaultConnectionConfiguration(this)
		)
	}

}

expect fun defaultConnectionConfiguration(accountBuilder: ConfigurationBuilder): Connection

fun createConfiguration(init: ConfigurationBuilder.() -> Unit): Configuration {
	val n = ConfigurationBuilder()
	n.init()
	return n.build()
}

fun createHalcyon(init: ConfigurationBuilder.() -> Unit): Halcyon {
	val n = ConfigurationBuilder()
	n.init()
	return Halcyon(n.build())
}