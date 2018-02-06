package org.tigase.jaxmpp.core

import org.tigase.jaxmpp.core.eventbus.EventBus
import org.tigase.jaxmpp.core.modules.ModulesManager

interface Context {

	val eventBus: EventBus

	val sessionObject: SessionObject

	val writer: PacketWriter

	val modules: ModulesManager

}