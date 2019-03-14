package tigase.halcyon.core.eventbus

interface EventHandler<in T : tigase.halcyon.core.eventbus.Event> {

	fun onEvent(sessionObject: tigase.halcyon.core.SessionObject, event: T)

}