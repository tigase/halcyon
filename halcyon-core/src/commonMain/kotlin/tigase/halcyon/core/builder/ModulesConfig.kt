package tigase.halcyon.core.builder

import tigase.halcyon.core.modules.HalcyonModule
import tigase.halcyon.core.modules.HalcyonModuleProvider
import tigase.halcyon.core.modules.ModulesManager
import tigase.halcyon.core.xmpp.modules.tick.TickModule

data class Item<M : HalcyonModule, B : Any>(
    val provider: HalcyonModuleProvider<M, B>,
    val configuration: (B.() -> Unit)? = null
)

@HalcyonConfigDsl
class ModulesConfigBuilder {

    private val providers = mutableMapOf<String, Any>()

    init {
        install(TickModule)
    }

    fun <M : HalcyonModule, B : Any> install(
        provider: HalcyonModuleProvider<M, B>,
        configuration: B.() -> Unit = {}
    ) {
        this.providers.remove(provider.TYPE)
        this.providers[provider.TYPE] = Item(provider, configuration)
    }
//
//
// 	}

    internal fun initializeModules(modulesManager: ModulesManager) {
        val modulesToConfigure =
            providers.values.filterIsInstance<Item<*, Any>>().extendForDependencies().filterIsInstance<Item<*, Any>>()
        modulesToConfigure.forEach { (provider, configuration) ->
            val originalModule = modulesManager.getModuleOrNull<HalcyonModule>(provider.TYPE)

            val currentModule = originalModule ?: provider.instance(modulesManager.context)
            provider.configure(currentModule, configuration ?: {})
            if (originalModule == null) {
                modulesManager.register(currentModule)
            }
        }

        modulesToConfigure.forEach { (provider, configuration) ->
            val module = modulesManager.getModuleOrNull<HalcyonModule>(provider.TYPE)
            provider.doAfterRegistration(module!!, modulesManager)
        }
    }
}
