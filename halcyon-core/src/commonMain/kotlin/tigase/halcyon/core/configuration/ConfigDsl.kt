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
package tigase.halcyon.core.configuration

import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xmpp.BareJID
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

internal class Alias<T>(private val delegate: KMutableProperty0<T>) {

	operator fun getValue(thisRef: Any?, property: KProperty<*>): T = delegate.get()

	operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
		delegate.set(value)
	}
}

abstract class AbstractConfigDsl(protected val configuration: Configuration) {

	protected class IntPasswordCallback(val pwd: String) : PasswordCallback {

		override fun getPassword(): String = pwd
	}

	var userJID: BareJID? by Alias(configuration::userJID)
	var resource: String? by Alias(configuration::resource)
	var domain: String? by Alias(configuration::domain)
	var passwordCallback: PasswordCallback? by Alias(configuration::passwordCallback)
	var password: String?
		set(value) {
			configuration.passwordCallback = IntPasswordCallback(value!!)
		}
		get() = when (configuration.passwordCallback) {
			is IntPasswordCallback -> {
				(configuration.passwordCallback as IntPasswordCallback).pwd
			}
			null -> null
			else -> throw HalcyonException("Cannot read password. Custom PasswordCallback is used.")
		}

}

expect class ConfigDsl(configuration: Configuration) : AbstractConfigDsl