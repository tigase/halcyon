package tigase.halcyon.core.builder

import tigase.halcyon.core.Context
import tigase.halcyon.core.modules.ModulesManager
import tigase.halcyon.core.modules.XmppModule

interface XmppModuleProvider<out M : XmppModule, Configuration : Any> {

	val TYPE: String

	fun instance(context: Context): M

	fun configure(module: @UnsafeVariance M, cfg: Configuration.() -> Unit)

}

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

internal fun ModulesManager.initializeModules() {
	val cfg = this.context.config.modulesConfigurator ?: return

	val builder = ModulesConfigBuilder(this, this.context)
	builder.cfg()

}