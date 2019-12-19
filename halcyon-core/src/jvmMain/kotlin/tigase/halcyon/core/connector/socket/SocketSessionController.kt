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
package tigase.halcyon.core.connector.socket

import tigase.halcyon.core.SessionObject
import tigase.halcyon.core.connector.AbstractSocketSessionController
import tigase.halcyon.core.connector.ConnectionErrorEvent
import tigase.halcyon.core.connector.socket.SocketConnector.Companion.SEE_OTHER_HOST_KEY
import tigase.halcyon.core.connector.socket.SocketConnector.Companion.XMLNS_START_TLS
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.SessionController
import tigase.halcyon.core.xmpp.StreamError
import tigase.halcyon.core.xmpp.modules.StreamErrorEvent
import tigase.halcyon.core.xmpp.modules.StreamFeaturesEvent
import tigase.halcyon.core.xmpp.modules.auth.SASLEvent
import tigase.halcyon.core.xmpp.modules.auth.SASLModule

class SocketSessionController(
	private val context: tigase.halcyon.core.Context, private val connector: SocketConnector
) : AbstractSocketSessionController(context, "tigase.halcyon.core.connector.socket.SocketSessionController") {

	override fun processAuthSuccessfull(event: SASLEvent.SASLSuccess) {
		connector.restartStream()
	}

	private fun isTLSAvailable(features: Element): Boolean =
		features.getChildrenNS("starttls", XMLNS_START_TLS) != null

	override fun processConnectionError(event: ConnectionErrorEvent) {
		log.log(Level.FINE, "Received connector exception: $event")

		context.sessionObject.clear(SessionObject.Scope.Connection)

//		context.modules.getModuleOrNull<StreamManagementModule>(StreamManagementModule.TYPE)?.reset()
//		context.modules.getModuleOrNull<SASLModule>(SASLModule.TYPE)?.clear()

		when (event) {
			is SocketConnectionErrorEvent.HostNotFount -> {
				log.info("Cannot find server in DNS")
				context.eventBus.fire(
					SessionController.SessionControllerEvents.ErrorStop(
						"Cannot find server in DNS"
					)
				)
			}
			else -> {
				context.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Connection error"))
			}
		}
	}

	override fun processStreamFeaturesEvent(event: StreamFeaturesEvent) {
		val authState = SASLModule.getAuthState(context.sessionObject)
		val connectionSecured = connector.secured
		val tlsAvailable: Boolean = isTLSAvailable(event.features)

		if (!connectionSecured && tlsAvailable) {
			connector.startTLS()
		} else super.processStreamFeaturesEvent(event)
	}

	override fun processStreamError(event: StreamErrorEvent) {
		when (event.condition) {
			StreamError.SEE_OTHER_HOST -> processSeeOtherHost(event)
			else -> super.processStreamError(event)
		}
	}

	private fun processSeeOtherHost(event: StreamErrorEvent) {
		val url = event.errorElement.value
		context.sessionObject.setProperty(SessionObject.Scope.Session, SEE_OTHER_HOST_KEY, url)

		context.eventBus.fire(
			SessionController.SessionControllerEvents.ErrorReconnect(
				"see-other-host: $url", immediately = true, force = true
			)
		)
	}

}