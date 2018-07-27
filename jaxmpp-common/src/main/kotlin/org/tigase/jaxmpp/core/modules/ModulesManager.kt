package org.tigase.jaxmpp.core.modules

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.exceptions.JaXMPPException
import org.tigase.jaxmpp.core.xml.Element

class ModulesManager {

	lateinit var context: Context

	private val modules: MutableMap<String, XmppModule> = HashMap()

	private val modulesToInitialize = mutableListOf<XmppModule>()

	fun register(module: XmppModule) {
		module.context = context
		modules[module.type] = module
		modulesToInitialize.add(module)
	}

	fun initModules() {
		synchronized(modulesToInitialize) {
			modulesToInitialize.forEach { xmppModule -> xmppModule.initialize() }
			modulesToInitialize.clear()
		}
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

	fun <T : XmppModule> getModule(type: String): T = this.modules[type] as T? ?: throw JaXMPPException(
			"Module '$type' not registered!")

	fun getModulesFor(element: Element): Array<XmppModule> {
		synchronized(modules) {
			return modules.values.filter { xmppModule ->
				(xmppModule.criteria != null && xmppModule.criteria!!.match(element))
			}.toTypedArray()
		}
	}

}