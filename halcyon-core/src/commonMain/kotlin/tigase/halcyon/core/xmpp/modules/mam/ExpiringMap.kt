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
package tigase.halcyon.core.xmpp.modules.mam

import kotlinx.datetime.Instant
import tigase.halcyon.core.TickEvent
import tigase.halcyon.core.eventbus.EventBus
import tigase.halcyon.core.eventbus.EventHandler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ExpiringMap<K, V>(
	private val map: MutableMap<K, V> = mutableMapOf(), private val minimalTime: Duration = 30.seconds,
) : MutableMap<K, V> by map {

	var expirationChecker: ((V) -> Boolean)? = null

	private val tickHandler = object : EventHandler<TickEvent> {
		override fun onEvent(event: TickEvent) {
			onTick(event)
		}
	}

	private var lastCallTime = Instant.DISTANT_PAST

	var eventBus: EventBus? = null
		set(value) {
			field?.unregister(tickHandler)
			field = value
			field?.register(TickEvent.TYPE, tickHandler)
		}

	private fun onTick(event: TickEvent) {
		if (lastCallTime + minimalTime <= event.eventTime) {
			lastCallTime = event.eventTime
			clearOutdated()
		}
	}

	@Suppress("unused")
	fun clearOutdated() {
		map.filter { (_, value) -> expirationChecker?.invoke(value) ?: false }
			.map { (key, _) -> key }
			.forEach { map.remove(it) }
	}

	override fun get(key: K): V? {
		val result = map.get(key) ?: return null
		if (expirationChecker?.invoke(result) == true) {
			map.remove(key)
			return null
		}
		return result
	}

}