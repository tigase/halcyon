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

import tigase.halcyon.core.ReflectionModuleManager
import tigase.halcyon.core.xml.Element
import kotlin.reflect.KClass

class ModulesManager {

	lateinit var context: tigase.halcyon.core.Context

	private val modulesByType: MutableMap<String, XmppModule> = HashMap()
	private val modulesByClass: MutableMap<KClass<*>, XmppModule> = HashMap()

	private val modulesOrdered = mutableListOf<XmppModule>()

	private val interceptors = mutableListOf<StanzaInterceptor>()

	private val modulesToInitialize = mutableListOf<XmppModule>()

	fun register(module: XmppModule) {
		modulesOrdered.add(module)
		modulesByType[module.type] = module
		modulesByClass[module::class] = module
		modulesToInitialize.add(module)
	}

	fun initModules() {
		modulesToInitialize.forEach(this::initModule)
		modulesToInitialize.clear()
	}

	private fun initModule(module: XmppModule) {
		module.initialize()
		if (module is HasInterceptors) {
			interceptors.addAll(module.stanzaInterceptors)
		}
	}

	fun getAvailableFeatures(): Array<String> {
		val tmp = mutableSetOf<String>()

		modulesByType.values.forEach { xmppModule ->
			val fs = xmppModule.features
			if (fs != null) tmp.addAll(fs)
		}

		return tmp.toTypedArray()
	}

	fun isRegistered(type: String): Boolean = this.modulesByType.containsKey(type)

	@ReflectionModuleManager
	fun isRegistered(cls: KClass<*>): Boolean = this.modulesByClass.containsKey(cls)

	@ReflectionModuleManager
	inline fun <reified T : XmppModule> isRegistered(): Boolean = isRegistered(T::class)

	fun getModules(): Collection<XmppModule> = this.modulesByType.values.toList()

	fun getModulesFor(element: Element): Array<XmppModule> {
		return modulesOrdered.filter { xmppModule ->
			(xmppModule.criteria != null && xmppModule.criteria!!.match(element))
		}.toTypedArray()
	}

	internal fun processReceiveInterceptors(element: Element): Element? {
		var tmp = element
		for (interceptor in interceptors) {
			tmp = interceptor.afterReceive(tmp) ?: return null
		}
		return tmp
	}

	internal fun processSendInterceptors(element: Element): Element {
		var tmp = element
		for (interceptor in interceptors) {
			tmp = interceptor.beforeSend(tmp)
		}
		return tmp
	}

	@Suppress("UNCHECKED_CAST")
	fun <T : XmppModule> getModule(type: String): T {
		val module = this.modulesByType[type] ?: throw throw NullPointerException("Module '$type' not registered!")
		return module as T
	}

	@ReflectionModuleManager
	@Suppress("UNCHECKED_CAST")
	fun <T : XmppModule> getModule(cls: KClass<T>): T {
		val module = this.modulesByClass[cls] ?: throw throw NullPointerException("Module not registered!")
		return module as T
	}

	@Suppress("UNCHECKED_CAST")
	fun <T : XmppModule> getModuleOrNull(type: String): T? {
		return this.modulesByType[type] as T?
	}

	@ReflectionModuleManager
	@Suppress("UNCHECKED_CAST")
	fun <T : XmppModule> getModuleOrNull(cls: KClass<T>): T? {
		return this.modulesByClass[cls] as T?
	}

	@ReflectionModuleManager
	inline fun <reified T : XmppModule> getModule(): T = getModule(T::class)

	operator fun <T : XmppModule> get(type: String): T = getModule(type)

	@ReflectionModuleManager
	operator fun <T : XmppModule> get(cls: KClass<T>): T = getModule(cls)
}