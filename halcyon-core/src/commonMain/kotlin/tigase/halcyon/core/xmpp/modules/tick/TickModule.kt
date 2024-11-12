package tigase.halcyon.core.xmpp.modules.tick

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.AbstractHalcyon.State.*
import tigase.halcyon.core.Context
import tigase.halcyon.core.HalcyonStateChangeEvent
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.HalcyonModule
import tigase.halcyon.core.modules.HalcyonModuleProvider
import tigase.halcyon.core.modules.ModulesManager

@HalcyonConfigDsl
interface TickModuleConfig {

    var tickTimer: TickTimer

}

interface TickTimer {
    fun startTimer(context: Context)
    fun stopTimer(context: Context)
}

class TickModule(override val context: Context) : HalcyonModule, TickModuleConfig {

    companion object : HalcyonModuleProvider<TickModule, TickModuleConfig> {
        override val TYPE = "halcyon:tick"
        override fun instance(context: Context): TickModule = TickModule(context)

        override fun configure(module: TickModule, cfg: TickModuleConfig.() -> Unit) =
            module.cfg()

        override fun doAfterRegistration(module: TickModule, moduleManager: ModulesManager) =
            module.context.eventBus.register(HalcyonStateChangeEvent, module::doOnHalcyonStateChange)
    }

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.tick.TickModule")

    override val type: String = TYPE
    override val features: Array<String>? = null

    override var tickTimer: TickTimer = createTickTimer()

    private fun doOnHalcyonStateChange(event: HalcyonStateChangeEvent) {
        when (event.newState) {
            Connecting -> {
                log.info("Starting Ticker.")
                tickTimer.startTimer(context)
            }

            Disconnecting, Disconnected, Stopped -> {
                log.info("Stopping Ticker.")
                tickTimer.stopTimer(context)
            }

            Connected -> {
                log.info("Halcyon connected.")
            }
        }
    }

}

expect fun createTickTimer(): TickTimer