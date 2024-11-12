package tigase.halcyon.core.xmpp.modules.tick

import tigase.halcyon.core.Context
import tigase.halcyon.core.TickEvent
import tigase.halcyon.core.logger.LoggerFactory
import java.util.*

actual fun createTickTimer(): TickTimer = DefaultTickTimer()

class DefaultTickTimer : TickTimer {

    private var timer: Timer? = null

    private var tickTask: TimerTask? = null

    private var tickCounter: Long = 0

    val logger = LoggerFactory.logger("DefaultTickTimer")

    override fun startTimer(context: Context) {
        if (timer != null) {
            logger.finest("Skipping starting new timer, already running, this/run: $this, task: ${tickTask}, timer: ${timer}")
            return
        }
        tickTask = object : TimerTask() {
            override fun run() {
                logger.finest("Firing timer, this/run: $this, task: ${tickTask}, timer: ${timer}")
                tick(context)
            }
        }
        timer = Timer("timer", true).also {
            it.schedule(tickTask, 2_000, 2_000)
        }
        logger.finest("Started timer, task: ${tickTask}, timer: ${timer}")
    }

    override fun stopTimer(context: Context) {
        logger.finest("Stopping timer [1], task: ${tickTask}, timer: ${timer}")
        tickTask?.cancel()
        logger.finest("Stopping timer [2], task: ${tickTask}, timer: ${timer}")
        tickTask = null
        timer?.purge()
        timer?.cancel()
        logger.finest("Stopping timer [3], task: ${tickTask}, timer: ${timer}")
        timer = null
        logger.finest("Stopping timer [4], task: ${tickTask}, timer: ${timer}")
    }

    private fun tick(context: Context) {
        context.eventBus.fire(TickEvent(++tickCounter))
    }

}