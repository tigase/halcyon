/*
 * halcyon-core
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

import tigase.halcyon.core.configuration.PasswordCallback
import tigase.halcyon.core.xmpp.BareJID

abstract class AbstractConfigurationBuilder internal constructor(val halcyon: AbstractHalcyon) {

	fun setAutoReconnect(value: Boolean): ConfigurationBuilder {
		halcyon.autoReconnect = value
		return this as ConfigurationBuilder
	}

	fun setJID(value: BareJID): ConfigurationBuilder {
		halcyon.config.userJID = value
		return this as ConfigurationBuilder
	}

	fun setPassword(value: String): ConfigurationBuilder {
		halcyon.config.passwordCallback = object : PasswordCallback {
			override fun getPassword(): String = value
		}
		return this as ConfigurationBuilder
	}

	fun setResource(value: String): ConfigurationBuilder {
		halcyon.config.resource = value
		return this as ConfigurationBuilder
	}

	fun setDomain(value: String): ConfigurationBuilder {
		halcyon.config.domain = value
		return this as ConfigurationBuilder
	}

}