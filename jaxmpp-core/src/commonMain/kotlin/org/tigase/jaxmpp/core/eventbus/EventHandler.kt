package org.tigase.jaxmpp.core.eventbus

import org.tigase.jaxmpp.core.SessionObject

interface EventHandler<in T : Event> {

	fun onEvent(sessionObject: SessionObject, event: T)

}