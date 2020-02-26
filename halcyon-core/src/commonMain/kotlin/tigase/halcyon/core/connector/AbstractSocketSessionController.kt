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

import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.Scope
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventHandler
import tigase.halcyon.core.requests.IQResult
import tigase.halcyon.core.xmpp.SessionController
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.StreamErrorEvent
import tigase.halcyon.core.xmpp.modules.StreamFeaturesEvent
import tigase.halcyon.core.xmpp.modules.auth.SASLEvent
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule

abstract class AbstractSocketSessionController(protected val halcyon: Halcyon, loggerName: String) : SessionController {

	protected val log = tigase.halcyon.core.logger.Logger(loggerName)

	private val eventsHandler: EventHandler<Event> = object : EventHandler<Event> {
		override fun onEvent(event: Event) {
			processEvent(event)
		}
	}

	protected open fun processStreamFeaturesEvent(event: StreamFeaturesEvent) {
		val authState = halcyon.getModule<SASLModule>(SASLModule.TYPE)?.saslContext?.state ?: SASLModule.State.Unknown
		val isResumptionAvailable =
			halcyon.getModule<StreamManagementModule>(StreamManagementModule.TYPE)?.resumptionContext?.isResumptionAvailable()
				?: false
		log.fine("authState=$authState; isResumptionAvailable=$isResumptionAvailable")
		if (authState == SASLModule.State.Unknown) {
			if (!isResumptionAvailable) {
				halcyon.modules.getModuleOrNull<StreamManagementModule>(StreamManagementModule.TYPE)?.reset()
			}
			halcyon.modules.getModule<SASLModule>(SASLModule.TYPE).startAuth()
		}
		if (authState == SASLModule.State.Success) {
			if (isResumptionAvailable) {
				halcyon.modules.getModule<StreamManagementModule>(StreamManagementModule.TYPE).resume()
			} else {
				bindResource()
			}
		}
	}

	private fun bindResource() {
		val resource = halcyon.config.resource
		halcyon.modules.getModule<BindModule>(BindModule.TYPE).bind(resource).response { result ->
			when (result) {
				is IQResult.Success<BindModule.BindResult> -> processBindSuccess(result.get()!!)
				else -> processBindError()
			}
		}.send()
	}

	private fun processEvent(event: Event) {
		when (event) {
			is ParseErrorEvent -> processParseError(event)
			is SASLEvent.SASLError -> processAuthError(event)
			is StreamErrorEvent -> processStreamError(event)
			is ConnectionErrorEvent -> processConnectionError(event)
			is StreamFeaturesEvent -> processStreamFeaturesEvent(event)
			is SASLEvent.SASLSuccess -> processAuthSuccessfull(event)
			is StreamManagementModule.StreamManagementEvent -> processStreamManagementEvent(event)
			is ConnectorStateChangeEvent -> processConnectorStateChangeEvent(event)
		}
	}

	private fun processConnectorStateChangeEvent(event: ConnectorStateChangeEvent) {
		if (event.oldState == State.Connected && (event.newState == State.Disconnected || event.newState == State.Disconnecting)) {
			log.fine("Checking conditions to force timeout")
			val isResumptionAvailable =
				halcyon.getModule<StreamManagementModule>(StreamManagementModule.TYPE)?.resumptionContext?.isResumptionAvailable()
					?: false
			if (!isResumptionAvailable) {
				halcyon.requestsManager.timeoutAll()
			}
		}
	}

	private fun processStreamManagementEvent(event: StreamManagementModule.StreamManagementEvent) {
		when (event) {
			is StreamManagementModule.StreamManagementEvent.Resumed -> halcyon.eventBus.fire(SessionController.SessionControllerEvents.Successful())
			is StreamManagementModule.StreamManagementEvent.Failed -> {
				halcyon.requestsManager.timeoutAll()
				bindResource()
			}
		}
	}

	private fun processBindSuccess(event: BindModule.BindResult) {
		halcyon.eventBus.fire(SessionController.SessionControllerEvents.Successful())
		halcyon.modules.getModuleOrNull<PresenceModule>(PresenceModule.TYPE)?.sendInitialPresence()
		halcyon.modules.getModuleOrNull<StreamManagementModule>(StreamManagementModule.TYPE)?.enable()
		halcyon.modules.getModuleOrNull<DiscoveryModule>(DiscoveryModule.TYPE)?.let {
			it.discoverServerFeatures()
			it.discoverAccountFeatures()
		}
	}

	protected abstract fun processAuthSuccessfull(event: SASLEvent.SASLSuccess)

	protected abstract fun processConnectionError(event: ConnectionErrorEvent)

	protected open fun processParseError(event: ParseErrorEvent) {
		halcyon.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Parse error"))
	}

	protected open fun processBindError() {
		halcyon.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Session bind error"))
	}

	protected open fun processAuthError(event: SASLEvent.SASLError) {
		halcyon.eventBus.fire(SessionController.SessionControllerEvents.ErrorStop("Authentication error."))
	}

	protected open fun processStreamError(event: StreamErrorEvent) {
		halcyon.clear(Scope.Connection)
		when (event.errorElement.name) {
			else -> halcyon.eventBus.fire(
				SessionController.SessionControllerEvents.ErrorReconnect(
					"Stream error: ${event.condition}"
				)
			)
		}
	}

	override fun start() {
		halcyon.eventBus.register(handler = eventsHandler)
	}

	override fun stop() {
		halcyon.eventBus.unregister(eventsHandler)
	}
}