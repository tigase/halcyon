package tigase.halcyon.core.modules

import tigase.halcyon.core.xml.Element

class ModulesManager {

	lateinit var context: tigase.halcyon.core.Context

	private val modules: MutableMap<String, tigase.halcyon.core.modules.XmppModule> = HashMap()

	private val modulesToInitialize = mutableListOf<tigase.halcyon.core.modules.XmppModule>()

	fun register(module: tigase.halcyon.core.modules.XmppModule) {
		module.context = context
		modules[module.type] = module
		modulesToInitialize.add(module)
	}

	fun initModules() {
		modulesToInitialize.forEach { xmppModule -> xmppModule.initialize() }
		modulesToInitialize.clear()
	}

	fun getAvailableFeatures(): Array<String> {
		val tmp = mutableSetOf<String>()

		modules.values.forEach { xmppModule ->
			val fs = xmppModule.features
			if (fs != null) tmp.addAll(fs)
		}

		return tmp.toTypedArray()
	}

	fun isRegistered(type: String): Boolean = this.modules.containsKey(type)

	fun <T : tigase.halcyon.core.modules.XmppModule> getModule(type: String): T {
		val module = this.modules[type]
			?: throw throw tigase.halcyon.core.exceptions.HalcyonException("Module '$type' not registered!")
		return module as T
	}

	fun getModulesFor(element: Element): Array<tigase.halcyon.core.modules.XmppModule> {
		return modules.values.filter { xmppModule ->
			(xmppModule.criteria != null && xmppModule.criteria!!.match(element))
		}.toTypedArray()
	}

}