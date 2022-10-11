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
package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.configuration.Configuration
import tigase.halcyon.core.toBase64

class SASLPlain : SASLMechanism {

	override val name = "PLAIN"

	override fun evaluateChallenge(input: String?, config: Configuration, saslContext: SASLContext): String? {
		if (saslContext.complete) return null

		val username = config.userJID?.localpart!!
		val password = config.passwordCallback!!.getPassword()

		saslContext.complete = true
		return buildString {
			config.authzIdJID?.let {
				append(it)
			}
			append('\u0000')
			append(username)
			append('\u0000')
			append(password)
		}.toBase64()
	}

	override fun isAllowedToUse(config: Configuration, saslContext: SASLContext): Boolean {
		return config.userJID != null && config.passwordCallback != null
	}

}