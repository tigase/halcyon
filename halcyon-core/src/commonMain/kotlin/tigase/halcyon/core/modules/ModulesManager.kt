/*
 * Tigase Halcyon XMPP Library
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core.modules

import tigase.halcyon.core.xml.Element

class ModulesManager {

	lateinit var context: tigase.halcyon.core.Context

	private val modules: MutableMap<String, XmppModule> = HashMap()

	private val modulesToInitialize = mutableListOf<XmppModule>()

	fun register(module: XmppModule) {
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

	@Suppress("UNCHECKED_CAST")
	fun <T : XmppModule> getModule(type: String): T {
		val module = this.modules[type]
			?: throw throw tigase.halcyon.core.exceptions.HalcyonException("Module '$type' not registered!")
		return module as T
	}

	@Suppress("UNCHECKED_CAST")
	fun <T : XmppModule> getModuleOrNull(type: String): T? {
		return this.modules[type] as T?
	}

	fun getModulesFor(element: Element): Array<XmppModule> {
		return modules.values.filter { xmppModule ->
			(xmppModule.criteria != null && xmppModule.criteria!!.match(element))
		}.toTypedArray()
	}

	operator fun <T : XmppModule> get(type: String): T = getModule(type)

}