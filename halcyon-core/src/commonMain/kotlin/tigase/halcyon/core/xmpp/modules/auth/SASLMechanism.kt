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
package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.SessionObject

interface SASLMechanism {
	/**
	 * Evaluating challenge received from server.
	 *
	 * @param input received data
	 * @param sessionObject current [SessionObject]
	 *
	 * @return calculated response
	 */
	fun evaluateChallenge(input: String?, sessionObject: SessionObject): String?

	/**
	 * This method is used to check if mechanism can be used with current
	 * session. For example if no username and passowrd is stored in
	 * sessionObject, then PlainMechanism can't be used.
	 *
	 * @param sessionObject current [SessionObject]
	 *
	 * @return `true` if mechanism can be used it current XMPP session.
	 */
	fun isAllowedToUse(sessionObject: SessionObject): Boolean

	/**
	 * Determines whether the authentication exchange has completed.
	 *
	 * @param sessionObject current [SessionObject]
	 *
	 * @return `true` if exchange is complete.
	 */
	fun isComplete(sessionObject: SessionObject): Boolean = SASLModule.getContext(sessionObject).complete

	/**
	 * Mechanism name.
	 */
	val name: String

	fun setComplete(sessionObject: SessionObject) {
		SASLModule.getContext(sessionObject).complete = true
	}

}