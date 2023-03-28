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
package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.Context
import tigase.halcyon.core.Scope
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.xml.Element

data class StreamFeaturesEvent(val features: Element) : Event(TYPE) {

	companion object : EventDefinition<StreamFeaturesEvent> {

		override val TYPE = "tigase.halcyon.core.xmpp.modules.StreamFeaturesEvent"
	}
}

@HalcyonConfigDsl
interface StreamFeaturesModuleConfig

class StreamFeaturesModule(override val context: Context) : XmppModule, StreamFeaturesModuleConfig {

	companion object : XmppModuleProvider<StreamFeaturesModule, StreamFeaturesModuleConfig> {

		override val TYPE = "StreamFeaturesModule"
		override fun instance(context: Context): StreamFeaturesModule = StreamFeaturesModule(context)

		override fun configure(module: StreamFeaturesModule, cfg: StreamFeaturesModuleConfig.() -> Unit) = module.cfg()
	}

	override val type = TYPE
	override val criteria = tigase.halcyon.core.modules.Criterion.and(
		tigase.halcyon.core.modules.Criterion.name("features"),
		tigase.halcyon.core.modules.Criterion.xmlns("http://etherx.jabber.org/streams")
	)

	var streamFeatures: Element? by propertySimple(Scope.Stream, null)
		private set

	override val features: Array<String>? = null

	override fun initialize() {}

	fun isFeatureAvailable(name: String, xmlns: String): Boolean = streamFeatures?.getChildrenNS(name, xmlns) != null

	override fun process(element: Element) {
		streamFeatures = element
		context.eventBus.fire(StreamFeaturesEvent(element))
	}
}