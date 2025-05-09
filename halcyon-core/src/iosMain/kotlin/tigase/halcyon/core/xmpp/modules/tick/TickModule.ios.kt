package tigase.halcyon.core.xmpp.modules.tick

import tigase.halcyon.core.Context
import tigase.halcyon.core.TickEvent

actual fun createTickTimer(): TickTimer = DefaultTickTimer()

class DefaultTickTimer : TickTimer {

    private var tickCounter: Long = 0

    override fun startTimer(context: Context) {
    }

    override fun stopTimer(context: Context) {
    }

    private fun tick(context: Context) {
        context.eventBus.fire(TickEvent(++tickCounter))
    }
}
