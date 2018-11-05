package org.tigase.jaxmpp.core

import org.tigase.jaxmpp.core.connector.AbstractConnector
import org.tigase.jaxmpp.core.connector.socket.SocketConnector
import org.tigase.jaxmpp.core.eventbus.EventBus
import java.util.*

actual class JaXMPP actual constructor() : AbstractJaXMPP() {

	override fun createConnector(): AbstractConnector {
		return SocketConnector(this)
	}

	val timer = Timer("timer", true)

	private val tickTask = object : TimerTask() {
		override fun run() {
			tick()
		}
	}

	init {
		eventBus.mode = EventBus.Mode.ThreadPerHandler
		timer.scheduleAtFixedRate(tickTask, 30_000, 30_000)
	}

}