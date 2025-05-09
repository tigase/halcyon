package tigase.halcyon.core.xmpp.modules.tick

import java.util.Timer
import java.util.TimerTask
import tigase.halcyon.core.Context
import tigase.halcyon.core.TickEvent
import tigase.halcyon.core.logger.LoggerFactory

actual fun createTickTimer(): TickTimer = DefaultTickTimer()

class DefaultTickTimer : TickTimer {

    private var timer: Timer? = null

    private var tickTask: TimerTask? = null

    private var tickCounter: Long = 0

    val logger = LoggerFactory.logger("DefaultTickTimer")

    override fun startTimer(context: Context) {
        if (timer != null) {
//            logger.finest("Skipping starting new timer, already running, this/run: $this, task: ${tickTask}, timer: ${timer}")
            return
        }
        tickTask = object : TimerTask() {
            override fun run() {
//                logger.finest("Firing timer, this/run: $this, task: ${tickTask}, timer: ${timer}")
                tick(context)
            }
        }
        timer = Timer("timer", true).also {
            it.schedule(tickTask, 1_000, 1_000)
        }
        logger.finest("Started timer, task: $tickTask, timer: $timer")
    }

    override fun stopTimer(context: Context) {
        logger.finest("Stopping timer, task: $tickTask, timer: $timer")
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
