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

class SASLAnonymous : SASLMechanism {

	override val name = "ANONYMOUS"

	override fun evaluateChallenge(input: String?, context: Context, config: Configuration, saslContext: SASLContext): String? {
		saslContext.complete = true
		return null
	}

	override fun isAllowedToUse(context: Context, config: Configuration, saslContext: SASLContext): Boolean = true

}