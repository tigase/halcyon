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
package tigase.halcyon.core

import tigase.halcyon.core.xmpp.BareJID

abstract class AbstractConfigurationBuilder internal constructor(val halcyon: AbstractHalcyon) {

	protected fun setProperty(key: String, value: Any?) {
		halcyon.sessionObject.setProperty(SessionObject.Scope.User, key, value)
	}

	fun setAutoReconnect(value: Boolean): ConfigurationBuilder {
		halcyon.autoReconnect = value
		return this as ConfigurationBuilder
	}

	fun setJID(value: BareJID): ConfigurationBuilder {
		setProperty(SessionObject.USER_BARE_JID, value)
		return this as ConfigurationBuilder
	}

	fun setPassword(value: String): ConfigurationBuilder {
		setProperty(SessionObject.PASSWORD, value)
		return this as ConfigurationBuilder
	}

	fun setResource(value: String): ConfigurationBuilder {
		setProperty(SessionObject.RESOURCE, value)
		return this as ConfigurationBuilder
	}

	fun setDomain(value: String): ConfigurationBuilder {
		setProperty(SessionObject.DOMAIN_NAME, value)
		return this as ConfigurationBuilder
	}

}