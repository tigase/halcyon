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
package tigase.halcyon.core.eventbus

interface EventBusInterface {

	companion object {

		const val ALL_EVENTS = "EventBus#ALL_EVENTS"
	}

	fun fire(event: Event)

	fun <T : Event> register(eventType: String = ALL_EVENTS, handler: EventHandler<T>)

	fun <T : Event> register(eventType: String = ALL_EVENTS, handler: (T) -> Unit)

	fun unregister(eventType: String = ALL_EVENTS, handler: EventHandler<*>)

	fun unregister(handler: EventHandler<*>)

}

inline fun <T : Event> handler(crossinline handler: (T) -> Unit): EventHandler<T> = object : EventHandler<T> {
	override fun onEvent(event: T) {
		handler.invoke(event)
	}
}