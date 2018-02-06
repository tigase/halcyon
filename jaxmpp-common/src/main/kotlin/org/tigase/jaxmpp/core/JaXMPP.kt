package org.tigase.jaxmpp.core

import org.tigase.jaxmpp.core.connector.AbstractConnector
import org.tigase.jaxmpp.core.connector.Connector
import org.tigase.jaxmpp.core.eventbus.EventBus
import org.tigase.jaxmpp.core.modules.ModulesManager
import org.tigase.jaxmpp.core.xml.Element

class JaXMPP : Context {

	constructor() {
		this.eventBus = EventBus()
		this.sessionObject = SessionObject()
		this.modules = ModulesManager()
		this.connector = Connector(this)
		this.writer = object : PacketWriter {
			override fun write(stanza: Element) {
				connector.send(stanza)
			}

			override fun write(stanza: Element, asyncCallback: AsyncCallback) {

				connector.send(stanza)
			}

			override fun write(stanza: Element, timeout: Long, asyncCallback: AsyncCallback) {
				connector.send(stanza)
			}
		}
	}

	constructor(eventBus: EventBus, sessionObject: SessionObject, writer: PacketWriter, modulesManager: ModulesManager,
				connector: AbstractConnector) {
		this.eventBus = eventBus
		this.sessionObject = sessionObject
		this.writer = writer
		this.modules = modulesManager
		this.connector = connector;
	}

	override val eventBus: EventBus
	override val sessionObject: SessionObject
	override val writer: PacketWriter
	override val modules: ModulesManager
	private val connector: AbstractConnector
}