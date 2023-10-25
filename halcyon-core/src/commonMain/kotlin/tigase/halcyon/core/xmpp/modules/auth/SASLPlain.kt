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

import tigase.halcyon.core.Context
import tigase.halcyon.core.configuration.Configuration
import tigase.halcyon.core.configuration.JIDPasswordSaslConfig
import tigase.halcyon.core.toBase64

class SASLPlain : SASLMechanism {

	override val name = "PLAIN"

	override fun evaluateChallenge(input: String?, context: Context, config: Configuration, saslContext: SASLContext): String? {
		if (saslContext.complete) return null
		val credentials = config.sasl as JIDPasswordSaslConfig

		val authcId = credentials.authcId ?: credentials.userJID.localpart!!
		val authzId = if (credentials.authcId != null) {
			credentials.userJID.toString()
		} else null
		val password = credentials.passwordCallback.invoke()

		saslContext.complete = true
		return buildString {
			if (authzId != null) append(authzId)
			append('\u0000')
			append(authcId)
			append('\u0000')
			append(password)
		}.toBase64()
	}

	override fun isAllowedToUse(context: Context, config: Configuration, saslContext: SASLContext): Boolean =
		config.sasl is JIDPasswordSaslConfig

}