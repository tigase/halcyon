package tigase.halcyon.core.modules

abstract class AbstractXmppModule(
	final override val type: String,
	final override val features: Array<String>,
	final override val criteria: tigase.halcyon.core.modules.Criteria
) : tigase.halcyon.core.modules.XmppModule {

	final override lateinit var context: tigase.halcyon.core.Context

	override fun initialize() {
	}

}