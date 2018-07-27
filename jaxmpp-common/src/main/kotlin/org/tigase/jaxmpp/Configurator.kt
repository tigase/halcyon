package org.tigase.jaxmpp

import org.tigase.jaxmpp.core.SessionObject
import org.tigase.jaxmpp.core.xmpp.BareJID

class Configurator(val sessionObject: SessionObject) {

	private fun setProperty(key: String, value: Any?) {
		sessionObject.setProperty(SessionObject.Scope.user, key, value)
	}

	var userJID: BareJID?
		set(value) {
			setProperty(SessionObject.USER_BARE_JID, value)
		}
		get() = sessionObject.getUserBareJid()

	var domain: String?
		set(value) {
			setProperty(SessionObject.DOMAIN_NAME, value)
		}
		get() = sessionObject.getProperty(SessionObject.DOMAIN_NAME)

	var resource: String?
		set(value) {
			setProperty(SessionObject.RESOURCE, value)
		}
		get() = sessionObject.getProperty(SessionObject.RESOURCE)

	var userPassword: String?
		set(value) {
			setProperty(SessionObject.PASSWORD, value)
		}
		get() = sessionObject.getProperty(SessionObject.PASSWORD)

}