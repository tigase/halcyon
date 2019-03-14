package tigase.halcyon

import tigase.halcyon.core.SessionObject
import tigase.halcyon.core.xmpp.BareJID

class Configurator(val sessionObject: tigase.halcyon.core.SessionObject) {

	private fun setProperty(key: String, value: Any?) {
		sessionObject.setProperty(tigase.halcyon.core.SessionObject.Scope.user, key, value)
	}

	var userJID: BareJID?
		set(value) {
			setProperty(tigase.halcyon.core.SessionObject.USER_BARE_JID, value)
		}
		get() = sessionObject.getUserBareJid()

	var domain: String?
		set(value) {
			setProperty(tigase.halcyon.core.SessionObject.DOMAIN_NAME, value)
		}
		get() = sessionObject.getProperty(tigase.halcyon.core.SessionObject.DOMAIN_NAME)

	var resource: String?
		set(value) {
			setProperty(tigase.halcyon.core.SessionObject.RESOURCE, value)
		}
		get() = sessionObject.getProperty(tigase.halcyon.core.SessionObject.RESOURCE)

	var userPassword: String?
		set(value) {
			setProperty(tigase.halcyon.core.SessionObject.PASSWORD, value)
		}
		get() = sessionObject.getProperty(tigase.halcyon.core.SessionObject.PASSWORD)

}