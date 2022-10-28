package tigase.halcyon.core.builder

import tigase.halcyon.core.Context
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.xmpp.modules.PingModule

object Module1 : XmppModuleProvider<PingModule, Any> {

	override val TYPE = "m1"

	override fun instance(context: Context): PingModule {
		TODO("Not yet implemented")
	}

	override fun configure(module: PingModule, cfg: Any.() -> Unit) {}

	override fun requiredModules(): List<XmppModuleProvider<XmppModule, out Any>> = listOf(Module2, Module3)
}

object Module2 : XmppModuleProvider<PingModule, Any> {

	override val TYPE = "m2"

	override fun instance(context: Context): PingModule {
		TODO("Not yet implemented")
	}

	override fun configure(module: PingModule, cfg: Any.() -> Unit) {}

	override fun requiredModules(): List<XmppModuleProvider<XmppModule, out Any>> = listOf(Module3)
}

object Module3 : XmppModuleProvider<PingModule, Any> {

	override val TYPE = "m3"

	override fun instance(context: Context): PingModule {
		TODO("Not yet implemented")
	}

	override fun configure(module: PingModule, cfg: Any.() -> Unit) {}

	override fun requiredModules(): List<XmppModuleProvider<XmppModule, out Any>> = listOf(Module4)
}

object Module4 : XmppModuleProvider<PingModule, Any> {

	override val TYPE = "m4"

	override fun instance(context: Context): PingModule {
		TODO("Not yet implemented")
	}

	override fun configure(module: PingModule, cfg: Any.() -> Unit) {}

}

