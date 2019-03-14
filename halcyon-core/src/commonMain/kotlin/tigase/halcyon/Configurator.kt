/*
 * Tigase Halcyon XMPP Library
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
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