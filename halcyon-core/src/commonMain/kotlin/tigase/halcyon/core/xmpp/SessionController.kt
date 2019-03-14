package tigase.halcyon.core.xmpp

interface SessionController {

	sealed class StopEverythingEvent : tigase.halcyon.core.eventbus.Event(TYPE) {

		companion object {
			const val TYPE = "tigase.halcyon.core.xmpp.SessionController.StopEverythingEvent"
		}

		class NormalStopEvent : StopEverythingEvent()
		class ErrorStopEvent(val message: String) : StopEverythingEvent()

	}

	fun start()

	fun stop()

}