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

interface SASLMechanism {

	/**
	 * Evaluating challenge received from server.
	 *
	 * @param input received data
	 * @param saslContext current [SASLContext]
	 *
	 * @return calculated response
	 */
	fun evaluateChallenge(input: String?, config: Configuration, saslContext: SASLContext): String?

	/**
	 * This method is used to check if mechanism can be used with current
	 * session. For example if no username and passowrd is stored in
	 * sessionObject, then PlainMechanism can't be used.
	 *
	 * @param config current [Configuration]
	 *
	 * @return `true` if mechanism can be used it current XMPP session.
	 */
	fun isAllowedToUse(config: Configuration, saslContext: SASLContext): Boolean

	/**
	 * Determines whether the authentication exchange has completed.
	 *
	 * @param saslContext current [SASLContext]
	 *
	 * @return `true` if exchange is complete.
	 */
	fun isComplete(saslContext: SASLContext): Boolean = saslContext.complete

	/**
	 * Mechanism name.
	 */
	val name: String

}