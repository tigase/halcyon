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

import tigase.halcyon.core.xmpp.SessionController
import tigase.halcyon.core.xmpp.modules.BindEvent
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.StreamErrorEvent
import tigase.halcyon.core.xmpp.modules.StreamFeaturesEvent
import tigase.halcyon.core.xmpp.modules.auth.AuthEvent
import tigase.halcyon.core.xmpp.modules.auth.SaslModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule

class SocketSessionController(
	private val context: tigase.halcyon.core.Context, private val connector: SocketConnector
) : SessionController {

	private val log = tigase.halcyon.core.logger.Logger("tigase.halcyon.core.connector.socket.SocketSessionController")

	private val eventHandler = object : tigase.halcyon.core.eventbus.EventHandler<tigase.halcyon.core.eventbus.Event> {
		override fun onEvent(
			sessionObject: tigase.halcyon.core.SessionObject, event: tigase.halcyon.core.eventbus.Event
		) {
			processEvent(event)
		}
	}

	private fun processEvent(event: tigase.halcyon.core.eventbus.Event) {
		when (event) {
			is tigase.halcyon.core.connector.ParseErrorEvent -> {
				log.info("Stream parse error. Stopping connection.")
				context.eventBus.fire(
					SessionController.StopEverythingEvent.ErrorStopEvent(
						"Stream parse error: ${event.errorMessage}"
					)
				)
			}
			is StreamErrorEvent -> processStreamError(event)
			is StreamFeaturesEvent -> processStreamFeaturesEvent(event)
			is AuthEvent.AuthSuccessEvent -> {
				connector.restartStream()
			}
			is BindEvent.Success -> {
				context.modules.getModule<StreamManagementModule>(StreamManagementModule.TYPE).enable()
			}
		}
	}

	private fun processStreamFeaturesEvent(event: StreamFeaturesEvent) {
		if (SaslModule.getAuthState(context.sessionObject) == SaslModule.State.Unknown) {
			context.modules.getModule<SaslModule>(SaslModule.TYPE).startAuth()
		}
		if (SaslModule.getAuthState(context.sessionObject) == SaslModule.State.Success) {
			context.modules.getModule<BindModule>(BindModule.TYPE).bind()
		}
	}

	private fun processStreamError(event: StreamErrorEvent) {
		when (event.error.name) {
			"see-other-host" -> doSeeOtherHost(event.error.value!!)
			else -> context.eventBus.fire(
				SessionController.StopEverythingEvent.ErrorStopEvent("Stream error: ${event.error.name}")
			)
		}
	}

	private fun doSeeOtherHost(newHost: String) {
		log.info("Received see-other-host. New host: $newHost")
		connector.stop()
		context.sessionObject.setProperty(SocketConnector.SERVER_HOST, newHost)
		connector.start()
	}

	override fun start() {
		context.eventBus.register(handler = eventHandler)
	}

	override fun stop() {
		context.eventBus.unregister(eventHandler)
	}
}