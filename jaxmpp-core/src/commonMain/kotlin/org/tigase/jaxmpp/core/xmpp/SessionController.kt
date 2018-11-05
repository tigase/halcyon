package org.tigase.jaxmpp.core.xmpp

import org.tigase.jaxmpp.core.eventbus.Event

interface SessionController {

	sealed class StopEverythingEvent : Event(TYPE) {

		companion object {
			const val TYPE = "org.tigase.jaxmpp.core.xmpp.SessionController.StopEverythingEvent";
		}

		class NormalStopEvent : StopEverythingEvent()
		class ErrorStopEvent(val message: String) : StopEverythingEvent()

	}

	fun start()

	fun stop()

}