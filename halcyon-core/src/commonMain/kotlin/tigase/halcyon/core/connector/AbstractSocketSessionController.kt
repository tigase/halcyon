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

import tigase.halcyon.core.Context
import tigase.halcyon.core.SessionObject
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventHandler
import tigase.halcyon.core.requests.Result
import tigase.halcyon.core.xmpp.SessionController
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.StreamErrorEvent
import tigase.halcyon.core.xmpp.modules.StreamFeaturesEvent
import tigase.halcyon.core.xmpp.modules.auth.SASLEvent
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule

open abstract class AbstractSocketSessionController(
	private val context: Context, private val loggerName: String
) : SessionController {

	protected val log = tigase.halcyon.core.logger.Logger(loggerName)

	private val eventsHandler: EventHandler<Event> = object : EventHandler<Event> {
		override fun onEvent(sessionObject: SessionObject, event: Event) {
			processEvent(event)
		}
	}

	protected open fun processStreamFeaturesEvent(event: StreamFeaturesEvent) {
		val authState = SASLModule.getAuthState(context.sessionObject)
		if (authState == SASLModule.State.Unknown) {
			context.modules.getModule<SASLModule>(SASLModule.TYPE).startAuth()
		}
		if (authState == SASLModule.State.Success) {
			val resourse = context.sessionObject.getProperty<String>(SessionObject.RESOURCE)
			context.modules.getModule<BindModule>(BindModule.TYPE).bind(resourse).response { _, _, result ->
				when (result) {
					is Result.Success<BindModule.BindResult> -> processBindSuccess(result.get()!!)
					else -> processBindError()
				}
			}.send()
		}
	}

	private fun processEvent(event: Event) {
		when (event) {
			is ParseErrorEvent -> processParseError(event)
			is SASLEvent.SASLError -> processAuthError(event)
			is StreamErrorEvent -> processStreamError(event)
			is ConnectionErrorEvent -> processConnectionError(event)
			is StreamFeaturesEvent -> processStreamFeaturesEvent(event)
			is SASLEvent.SASLSuccess -> processAuthSuccessfull(event)
		}
	}

	private fun processBindSuccess(event: BindModule.BindResult) {
		context.eventBus.fire(SessionController.SessionControllerEvents.Successful())
		context.modules.getModuleOrNull<PresenceModule>(PresenceModule.TYPE)?.sendInitialPresence()
		context.modules.getModuleOrNull<StreamManagementModule>(StreamManagementModule.TYPE)?.enable()
	}

	protected abstract fun processAuthSuccessfull(event: SASLEvent.SASLSuccess)

	protected abstract fun processConnectionError(event: ConnectionErrorEvent)

	protected open fun processParseError(event: ParseErrorEvent) {
		context.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Parse error"))
	}

	protected open fun processBindError() {
		context.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Session bind error"))
	}

	protected open fun processAuthError(event: SASLEvent.SASLError) {
		context.eventBus.fire(SessionController.SessionControllerEvents.ErrorStop("Authentication error."))
	}

	protected open fun processStreamError(event: StreamErrorEvent) {
		context.sessionObject.clear(SessionObject.Scope.Session)
		when (event.errorElement.name) {
			else -> context.eventBus.fire(
				SessionController.SessionControllerEvents.ErrorReconnect(
					"Stream error: ${event.condition}"
				)
			)
		}
	}

	override fun start() {
		context.eventBus.register(handler = eventsHandler)
	}

	override fun stop() {
		context.eventBus.unregister(eventsHandler)
	}
}