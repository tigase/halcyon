package org.tigase.jaxmpp.core.eventbus

import java.util.concurrent.ConcurrentHashMap

actual fun createHandlersMap(): MutableMap<String, MutableCollection<EventHandler<*>>> = ConcurrentHashMap();

actual fun createHandlersList(): MutableCollection<EventHandler<*>> = HashSet()
