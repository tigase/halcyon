package tigase.halcyon.core.xmpp.modules.tick

import java.util.Timer
import java.util.TimerTask
import tigase.halcyon.core.Context
import tigase.halcyon.core.TickEvent

class MultiContextTickTimer : TickTimer {

    private val contexts: MutableSet<Context> = mutableSetOf()

    private var timer: Timer? = null

    private var tickTask: TimerTask? = null

    private var tickCounter: Long = 0

    override fun startTimer(context: Context) {
        if (contexts.isEmpty()) {
            startInternalTimer()
        }
        contexts.add(context)
    }

    override fun stopTimer(context: Context) {
        contexts.remove(context)
        if (contexts.isEmpty()) {
            stopAll()
        }
    }

    private fun startInternalTimer() {
        tickTask = object : TimerTask() {
            override fun run() {
                tick()
            }
        }
        timer = Timer("timer", true).also {
            it.scheduleAtFixedRate(tickTask, 1_000, 1_000)
        }
    }

    fun stopAll() {
        tickTask?.cancel()
        tickTask = null
        timer?.purge()
        timer?.cancel()
        timer = null
    }

    private fun tick() {
        ++tickCounter
        for (context in contexts) {
            context.eventBus.fire(TickEvent(tickCounter))
        }
    }
}
