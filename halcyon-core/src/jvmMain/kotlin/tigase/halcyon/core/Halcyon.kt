package tigase.halcyon.core

import tigase.halcyon.core.connector.socket.SocketConnector
import java.util.*

actual class Halcyon actual constructor() : tigase.halcyon.core.AbstractHalcyon() {

	override fun createConnector(): tigase.halcyon.core.connector.AbstractConnector {
		return SocketConnector(this)
	}

	val timer = Timer("timer", true)

	private val tickTask = object : TimerTask() {
		override fun run() {
			tick()
		}
	}

	init {
		eventBus.mode = tigase.halcyon.core.eventbus.EventBus.Mode.ThreadPerHandler
		timer.scheduleAtFixedRate(tickTask, 30_000, 30_000)
	}

}