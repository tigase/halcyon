package tigase.halcyon.core.builder

import tigase.halcyon.core.modules.ModulesManager
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider

data class Item<M : XmppModule, B : Any>(
	val provider: XmppModuleProvider<M, B>,
	val configuration: (B.() -> Unit)? = null,
)

@HalcyonConfigDsl
class ModulesConfigBuilder {

	private val providers = mutableMapOf<String, Any>()

	fun <M : XmppModule, B : Any> install(
		provider: XmppModuleProvider<M, B>,
		configuration: B.() -> Unit = {},
	) {
		this.providers.remove(provider.TYPE)
		this.providers[provider.TYPE] = Item(provider, configuration)
	}
//
//
//	}

	internal fun initializeModules(modulesManager: ModulesManager) {
		providers.values.filterIsInstance<Item<*, Any>>()
			.extendForDependencies()
			.filterIsInstance<Item<*, Any>>()
			.forEach { (provider, configuration) ->
				val originalModule = modulesManager.getModuleOrNull<XmppModule>(provider.TYPE)

				val currentModule = originalModule ?: provider.instance(modulesManager.context)
				provider.configure(currentModule, configuration ?: {})
				if (originalModule == null) {
					modulesManager.register(currentModule)
				}
			}
	}

}

