package org.tigase.jaxmpp.core.connector.socket

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.SessionObject
import org.tigase.jaxmpp.core.eventbus.Event
import org.tigase.jaxmpp.core.eventbus.EventHandler
import org.tigase.jaxmpp.core.logger.Logger
import org.tigase.jaxmpp.core.xmpp.SessionController
import org.tigase.jaxmpp.core.xmpp.modules.StreamErrorEvent
import org.tigase.jaxmpp.core.xmpp.modules.StreamFeaturesEvent
import org.tigase.jaxmpp.core.xmpp.modules.auth.AuthEvent
import org.tigase.jaxmpp.core.xmpp.modules.auth.SaslModule

class SocketSessionController(private val context: Context,
							  private val connector: SocketConnector) : SessionController {

	private val log = Logger("org.tigase.jaxmpp.core.connector.socket.SocketSessionController")

	private val eventHandler = object : EventHandler<Event> {
		override fun onEvent(sessionObject: SessionObject, event: Event) {
			processEvent(event)
		}
	}

	private fun processEvent(event: Event) {
		when (event) {
			is StreamErrorEvent -> processStreamError(event)
			is StreamFeaturesEvent -> processStreamFeaturesEvent(event)
			is AuthEvent.AuthSuccessEvent -> {
				connector.restartStream()
			}
		}
	}

	private fun processStreamFeaturesEvent(event: StreamFeaturesEvent) {
		if (SaslModule.getAuthState(context.sessionObject) == SaslModule.State.Unknown) {
			context.modules.getModule<SaslModule>(SaslModule.TYPE).startAuth()
		}
	}

	private fun processStreamError(event: StreamErrorEvent) {
		when (event.error.name) {
			"see-other-host" -> doSeeOtherHost(event.error.value!!)
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