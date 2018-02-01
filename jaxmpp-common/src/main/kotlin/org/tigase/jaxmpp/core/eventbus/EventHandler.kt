package org.tigase.jaxmpp.core.eventbus

interface EventHandler<in T : Event> {

	fun onEvent(event: T)

}