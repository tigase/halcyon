package tigase.halcyon.core.xmpp.modules.tick

import tigase.halcyon.core.Context
import tigase.halcyon.core.TickEvent
import java.util.*

actual fun createTickTimer(): TickTimer = DefaultTickTimer()

class DefaultTickTimer : TickTimer {

    private var timer: Timer? = null

    private var tickTask: TimerTask? = null

    private var tickCounter: Long = 0

    override fun startTimer(context: Context) {
        tickTask = object : TimerTask() {
            override fun run() {
                tick(context)
            }
        }
        timer = Timer("timer", true).also {
            it.schedule(tickTask, 2_000, 2_000)
        }
    }

    override fun stopTimer(context: Context) {
        tickTask?.cancel()
        tickTask = null
        timer?.purge()
        timer?.cancel()
        timer = null
    }

    private fun tick(context: Context) {
        context.eventBus.fire(TickEvent(++tickCounter))
    }

}