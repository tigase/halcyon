/*
 * halcyon-core
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
import tigase.halcyon.core.builder.ConfigurationException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.filter.StanzaFilterProcessor
import tigase.halcyon.core.xml.Element
import kotlin.reflect.KClass

class ModulesManager {

    private val log = LoggerFactory.logger("tigase.halcyon.core.modules.ModulesManager")

    lateinit var context: tigase.halcyon.core.Context

    private val modulesByType: MutableMap<String, HalcyonModule> = HashMap()
    private val modulesByClass: MutableMap<KClass<*>, HalcyonModule> = HashMap()

    private val modulesOrdered = mutableListOf<XmppModule>()

    private val incomingStanzaFilters = StanzaFilterProcessor()

    private val outgoingStanzaFilters = StanzaFilterProcessor()

    fun register(module: HalcyonModule) {
        log.fine { "Registering module '${module.type}'" }
        if (modulesByType.containsKey(module.type)) throw ConfigurationException("Module '${module.type}' is installed already.")
        modulesByType[module.type] = module
        modulesByClass[module::class] = module
        if (module is XmppModule) modulesOrdered.add(module)
    }

    internal fun processReceiveInterceptors(element: Element, result: (Result<Element?>) -> Unit) {
        incomingStanzaFilters.doFilters(element, result)
    }

    internal fun processOutgoingFilters(element: Element, result: (Result<Element?>) -> Unit) {
        outgoingStanzaFilters.doFilters(element, result)
    }

    fun registerInterceptors(stanzaInterceptors: Array<StanzaInterceptor>) {
        stanzaInterceptors.forEach { interceptor ->
            outgoingStanzaFilters.addToChain(BeforeSendInterceptorFilter(interceptor))
            incomingStanzaFilters.addToChain(AfterReceiveInterceptorFilter(interceptor))
        }
    }

    fun registerOutgoingFilter(filter: StanzaFilter) = outgoingStanzaFilters.addToChain(filter)

    fun registerIncomingFilter(filter: StanzaFilter) = incomingStanzaFilters.addToChain(filter)

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

    fun getModules(): Collection<HalcyonModule> = this.modulesByType.values.toList()

    fun getModulesFor(element: Element): Array<XmppModule> {
        return modulesOrdered.filter { xmppModule ->
            (xmppModule.criteria != null && xmppModule.criteria!!.match(element))
        }.toTypedArray()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : HalcyonModule> getModule(type: String): T {
        val module = this.modulesByType[type] ?: throw throw NullPointerException("Module '$type' not registered!")
        return module as T
    }

    @ReflectionModuleManager
    @Suppress("UNCHECKED_CAST")
    fun <T : HalcyonModule> getModule(cls: KClass<T>): T {
        val module = this.modulesByClass[cls] ?: throw throw NullPointerException("Module not registered!")
        return module as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : HalcyonModule> getModuleOrNull(type: String): T? {
        return this.modulesByType[type] as T?
    }

    @ReflectionModuleManager
    @Suppress("UNCHECKED_CAST")
    fun <T : HalcyonModule> getModuleOrNull(cls: KClass<T>): T? {
        return this.modulesByClass[cls] as T?
    }

    @ReflectionModuleManager
    inline fun <reified T : HalcyonModule> getModule(): T = getModule(T::class)

    operator fun <T : HalcyonModule> get(type: String): T = getModule(type)

    fun <T : HalcyonModule> getModule(provider: HalcyonModuleProvider<T, out Any>): T = getModule(provider.TYPE)

    @ReflectionModuleManager
    operator fun <T : HalcyonModule> get(cls: KClass<T>): T = getModule(cls)

    fun <T : HalcyonModule> getModuleOrNull(provider: HalcyonModuleProvider<T, out Any>): T? =
        getModuleOrNull(provider.TYPE)

}