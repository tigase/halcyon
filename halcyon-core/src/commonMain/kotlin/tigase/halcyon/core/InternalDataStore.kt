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
package tigase.halcyon.core

import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.utils.Lock

class InternalDataStore {

    private val log = LoggerFactory.logger("tigase.halcyon.core.SessionObject")

    private val properties: MutableMap<String, Entry> = HashMap()

    private val lock = Lock()

    fun clear() {
        clear(Int.MAX_VALUE)
    }

    fun clear(scope: Scope) {
        clear(scope.ordinal)
    }

    private fun clear(ordinal: Int) {
        val scopes = Scope.values()
            .filter { s -> s.ordinal <= ordinal }
            .toTypedArray()
        log.fine { "Clearing ${scopes.asList()}" }
        lock.withLock {
            val toRemove = this.properties.entries.filter {
                scopes.contains(it.value.scope)
            }.map { it.key }
            toRemove.onEach { this.properties.remove(it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getData(scope: Scope?, key: String): T? {
        val entry = lock.withLock { this.properties[key] }
        return if (entry == null) {
            null
        } else if (scope == null || scope == entry.scope) {
            entry.value as T?
        } else {
            null
        }
    }

    fun <T> getData(key: String): T? = getData<T>(null, key)

    fun setData(scope: Scope, key: String, value: Any?): InternalDataStore {
        lock.withLock {
            if (value == null) {
                this.properties.remove(key)
            } else {
                this.properties[key] = Entry(scope, value)
            }
        }
        return this
    }

    override fun toString(): String = "AbstractSessionObject{properties=$properties}"

    private data class Entry(val scope: Scope, val value: Any?)
}
