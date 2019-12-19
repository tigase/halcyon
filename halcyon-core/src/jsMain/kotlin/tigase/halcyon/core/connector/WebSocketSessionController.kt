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
package tigase.halcyon.core.connector

import tigase.halcyon.core.SessionObject
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.xmpp.SessionController
import tigase.halcyon.core.xmpp.modules.auth.SASLEvent

class WebSocketSessionController(
	private val context: tigase.halcyon.core.Context, private val connector: WebSocketConnector
) : AbstractSocketSessionController(context, "tigase.halcyon.core.connector.WebSocketSessionController") {

	override fun processAuthSuccessfull(event: SASLEvent.SASLSuccess) {
		connector.restartStream()
	}

	override fun processConnectionError(event: ConnectionErrorEvent) {
		log.log(Level.FINE, "Received connector exception: $event")
		context.sessionObject.clear(SessionObject.Scope.Connection)
		context.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Connection error"))
	}

}