package org.tigase.jaxmpp.core.eventbus

import org.tigase.jaxmpp.core.SessionObject

actual class EventBus actual constructor(sessionObject: SessionObject) : AbstractEventBus(sessionObject) {

	override fun createHandlersMap(): MutableMap<String, MutableSet<EventHandler<*>>> = HashMap()

	override fun createHandlersSet(): MutableSet<EventHandler<*>> = HashSet()

}