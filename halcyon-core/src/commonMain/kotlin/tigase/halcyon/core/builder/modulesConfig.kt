package tigase.halcyon.core.builder

import tigase.halcyon.core.Context
import tigase.halcyon.core.modules.ModulesManager
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider

@ConfigurationDSLMarker
class ModulesConfigBuilder(val modulesManager: ModulesManager, val context: Context)

fun <M : XmppModule, B : Any> ModulesConfigBuilder.install(
	provider: XmppModuleProvider<M, B>,
	configuration: B.() -> Unit = {},
) {
	val originalModule = modulesManager.getModuleOrNull<M>(provider.TYPE)

	val currentModule = originalModule ?: provider.instance(this.context)
	provider.configure(currentModule, configuration)
	if (originalModule == null) {
		modulesManager.register(currentModule)
	}
}

internal fun ModulesManager.initializeModules(configurator: ConfigurationBuilder) {
	configurator.modulesConfigBuilders.forEach { cfg ->
		val builder = ModulesConfigBuilder(this, this.context)
		builder.cfg()
	}

}