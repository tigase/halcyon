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

data class StreamErrorEvent(val error: Element) : tigase.halcyon.core.eventbus.Event(TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.xmpp.modules.StreamErrorEvent"
	}
}

class StreamErrorModule : tigase.halcyon.core.modules.XmppModule {

	companion object {
		const val TYPE = "StreamErrorModule"
	}

	override val type = TYPE
	override lateinit var context: tigase.halcyon.core.Context
	override val criteria = tigase.halcyon.core.modules.Criterion.and(
		tigase.halcyon.core.modules.Criterion.name("error"), tigase.halcyon.core.modules.Criterion.xmlns("http://etherx.jabber.org/streams")
	)
	override val features: Array<String>? = null

	override fun initialize() {}

	override fun process(element: Element) {
		context.eventBus.fire(StreamErrorEvent(element.getFirstChild()!!))
	}
}