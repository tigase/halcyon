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
package tigase.halcyon.samples.tigase.convene

import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventBusInterface
import tigase.halcyon.core.eventbus.EventHandler
import tigase.halcyon.core.eventbus.NoContextEventBus
import java.util.concurrent.ConcurrentHashMap

class MultiEventBus : NoContextEventBus() {

	private val eventBuses: MutableSet<EventBusInterface> = ConcurrentHashMap.newKeySet()

	override fun createHandlersMap(): MutableMap<String, MutableSet<EventHandler<*>>> = ConcurrentHashMap()

	override fun createHandlersSet(): MutableSet<EventHandler<*>> = ConcurrentHashMap.newKeySet()

	override fun updateBeforeFire(event: Event) {}

	private val handler = object : EventHandler<Event> {
		override fun onEvent(event: Event) {
			this@MultiEventBus.fire(event)
		}
	}

	fun add(eventBus: EventBusInterface) {
		val added = eventBuses.add(eventBus)
		if (added) {
			eventBus.register(handler = handler)
		}
	}

	fun remove(eventBus: EventBusInterface) {
		eventBus.unregister(handler)
		eventBuses.remove(eventBus)
	}

}