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
package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.xml.Element

data class StreamFeaturesEvent(val features: Element) : tigase.halcyon.core.eventbus.Event(TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.xmpp.modules.StreamFeaturesEvent"
	}
}

class StreamFeaturesModule : tigase.halcyon.core.modules.XmppModule {

	companion object {
		const val TYPE = "StreamFeaturesModule"
		const val FEATURES_KEY = "StreamFeaturesModule.Features"

		fun getFeatures(sessionObject: tigase.halcyon.core.SessionObject): Element? =
			sessionObject.getProperty<Element>(FEATURES_KEY)

		fun isFeatureAvailable(
			sessionObject: tigase.halcyon.core.SessionObject, name: String, xmlns: String
		): Boolean = getFeatures(
			sessionObject
		)?.getChildrenNS(name, xmlns) != null
	}

	override val type = TYPE
	override lateinit var context: tigase.halcyon.core.Context
	override val criteria = tigase.halcyon.core.modules.Criterion.and(
		tigase.halcyon.core.modules.Criterion.name("features"),
		tigase.halcyon.core.modules.Criterion.xmlns("http://etherx.jabber.org/streams")
	)
	override val features: Array<String>? = null

	override fun initialize() {}

	override fun process(element: Element) {
		context.sessionObject.setProperty(
			tigase.halcyon.core.SessionObject.Scope.Stream, FEATURES_KEY, element
		)
		context.eventBus.fire(StreamFeaturesEvent(element))
	}
}