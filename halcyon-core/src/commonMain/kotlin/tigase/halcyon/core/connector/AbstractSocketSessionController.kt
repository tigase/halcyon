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
@file:Suppress("UnusedVariable", "UNUSED_VARIABLE", "UnusedParameter", "UNUSED_PARAMETER", "unused")

package tigase.halcyon.core.connector

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.Scope
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventHandler
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.*
import tigase.halcyon.core.xmpp.modules.auth.SASL2Module
import tigase.halcyon.core.xmpp.modules.auth.SASLEvent
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.modules.roster.RosterModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementEvent
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import tigase.halcyon.core.xmpp.toBareJID

abstract class AbstractSocketSessionController(final override val halcyon: AbstractHalcyon, loggerName: String) :
	SessionController {

	protected val log = LoggerFactory.logger(loggerName)

	private val eventsHandler: EventHandler<Event> = object : EventHandler<Event> {
		override fun onEvent(event: Event) {
			processEvent(event)
		}
	}

	protected open fun processStreamFeaturesEvent(event: StreamFeaturesEvent) {
		val authState = halcyon.authContext.state
		val isResumptionAvailable =
			halcyon.getModuleOrNull(StreamManagementModule)?.isResumptionAvailable() ?: false


		log.info { "authState=$authState; isResumptionAvailable=$isResumptionAvailable" }

		if (authState == tigase.halcyon.core.xmpp.modules.auth.State.Unknown) {
			if (!isResumptionAvailable) {
				halcyon.getModuleOrNull(StreamManagementModule)?.reset()
			}

			val sasl1Module = halcyon.getModuleOrNull(SASLModule)
			val sasl2Module = halcyon.getModuleOrNull(SASL2Module)
			val registrationModule = halcyon.getModuleOrNull(InBandRegistrationModule)

			if (sasl2Module?.isAllowed(event.features) == true) {
				sasl2Module.startAuth(event.features)
			} else if (sasl2Module?.inProgress != true && sasl1Module?.isAllowed(event.features) == true) {
				sasl1Module.startAuth(event.features)
			} else if (registrationModule?.isAllowed(event.features) == true && halcyon.config.registration != null) {
				processInBandRegistration()
			} else throw HalcyonException("Cannot find supported auth or registration method.")
		}
		if (authState == tigase.halcyon.core.xmpp.modules.auth.State.Success) {
			if (isResumptionAvailable) {
				halcyon.getModule(StreamManagementModule).resume()
			} else if (halcyon.getModuleOrNull(StreamFeaturesModule)
					?.isFeatureAvailable("bind", BindModule.XMLNS) == true
			) {
				bindResource()
			}
		}
	}

	private fun processInBandRegistration() {
		val registrationModule = halcyon.getModule(InBandRegistrationModule)
		val reg = halcyon.config.registration!!
		registrationModule.requestRegistrationForm(reg.domain.toBareJID()).response {
			it.onSuccess { requestForm ->
				reg.formHandler?.invoke(requestForm)
				reg.formHandlerWithResponse?.invoke(requestForm)?.let { resultForm ->
					registrationModule.submitRegistrationForm(reg.domain.toBareJID(), resultForm)
						.response { registrationResponse ->
							registrationResponse.onSuccess {
								log.info("Account registered")
								halcyon.disconnect()
							}
							registrationResponse.onFailure {
								log.info(it) { "Cannot register account." }
								throw HalcyonException("Cannot register account", it)
							}
						}.send()
				}
			}
			it.onFailure {
				log.info(it) { "Cannot register account." }
				throw HalcyonException("Cannot register account", it)
			}
		}.send()

	}

	private fun bindResource() {
		halcyon.getModuleOrNull(BindModule)?.bind()?.send() ?: throw HalcyonException("BindModule is required.")
	}

	private fun processEvent(event: Event) {
		try {
			when (event) {
				is ParseErrorEvent -> processParseError(event)
				is SASLEvent.SASLError -> processAuthError(event)
				is StreamErrorEvent -> processStreamError(event)
				is ConnectionErrorEvent -> processConnectionError(event)
				is StreamFeaturesEvent -> processStreamFeaturesEvent(event)
				is SASLEvent.SASLSuccess -> processAuthSuccessfull(event)
				is StreamManagementEvent -> processStreamManagementEvent(event)
				is ConnectorStateChangeEvent -> processConnectorStateChangeEvent(event)
				is BindEvent.Success -> processBindSuccess(event.jid, event.inlineProtocol)
				is BindEvent.Failure -> processBindError()
			}
		} catch (e: XMPPException) {
			log.severe(e) { "Cannot establish connection" }
			halcyon.eventBus.fire(SessionController.SessionControllerEvents.ErrorStop("Error in session processing"))
		} catch (e: HalcyonException) {
			log.severe(e) { "Cannot establish connection" }
			halcyon.eventBus.fire(SessionController.SessionControllerEvents.ErrorStop("Error in session processing"))
		}
	}

	private fun processConnectorStateChangeEvent(event: ConnectorStateChangeEvent) {
		if (event.oldState == State.Connected && (event.newState == State.Disconnected || event.newState == State.Disconnecting)) {
			log.fine { "Checking conditions to force timeout" }
			val isResumptionAvailable =
				halcyon.getModuleOrNull(StreamManagementModule)?.isResumptionAvailable()?:false
			if (!isResumptionAvailable) {
				halcyon.requestsManager.timeoutAll()
			}
		}
	}

	private fun processStreamManagementEvent(event: StreamManagementEvent) {
		when (event) {
			is StreamManagementEvent.Resumed -> halcyon.eventBus.fire(SessionController.SessionControllerEvents.Successful())
			is StreamManagementEvent.Failed -> {
				halcyon.requestsManager.timeoutAll()
				bindResource()
			}

			is StreamManagementEvent.Enabled -> {}
		}
	}

	private fun processBindSuccess(jid: JID, inlineProtocol: Boolean) {
		log.info("Binded")
		halcyon.getModuleOrNull(DiscoveryModule)?.let {
			it.discoverServerFeatures()
			it.discoverAccountFeatures()
		}
		halcyon.eventBus.fire(SessionController.SessionControllerEvents.Successful())
		halcyon.getModuleOrNull(PresenceModule)?.sendInitialPresence()
		halcyon.getModuleOrNull(RosterModule)?.rosterGet()?.send()
		if (!inlineProtocol) {
			halcyon.modules.getModuleOrNull(StreamManagementModule)?.enable()
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
		log.fine("Processing stream error: $event")
		halcyon.clear(Scope.Connection)
		when (event.errorElement.name) {
			else -> halcyon.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Stream error: ${event.condition}"))
		}
	}

	override fun start() {
		halcyon.eventBus.register(handler = eventsHandler)
		log.info { "Started session controller" }
	}

	override fun stop() {
		halcyon.eventBus.unregister(eventsHandler)
		log.info { "Stopped session controller" }
	}
}