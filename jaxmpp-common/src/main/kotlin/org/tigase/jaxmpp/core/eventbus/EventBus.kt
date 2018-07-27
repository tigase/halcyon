package org.tigase.jaxmpp.core.eventbus

import org.tigase.jaxmpp.core.SessionObject

expect class EventBus(sessionObject: SessionObject) : AbstractEventBus
