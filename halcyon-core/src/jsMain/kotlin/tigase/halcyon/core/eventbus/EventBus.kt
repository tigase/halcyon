package tigase.halcyon.core.eventbus

actual class EventBus actual constructor(sessionObject: tigase.halcyon.core.SessionObject) :
	tigase.halcyon.core.eventbus.AbstractEventBus(sessionObject) {

	override fun createHandlersMap(): MutableMap<String, MutableSet<tigase.halcyon.core.eventbus.EventHandler<*>>> =
		HashMap()

	override fun createHandlersSet(): MutableSet<tigase.halcyon.core.eventbus.EventHandler<*>> = HashSet()

}