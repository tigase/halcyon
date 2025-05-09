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
package tigase.halcyon.core.logger.internal

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerInternal

class LoggerSPIBuffer(val bufferSize: Int = 100) {

    data class Entry(
        val timestamp: Instant,
        val level: Level,
        val loggerName: String,
        val msg: String,
        val caught: Throwable?
    )

    var spiFactory: ((String, Boolean) -> LoggerInternal) = { name, enabled ->
        DefaultLoggerSPI(name, enabled)
    }

    private val buffer = mutableListOf<Entry>()

    var callback: ((Entry) -> Unit)? = null

    private fun add(entry: Entry) {
        buffer.add(entry)
        if (buffer.size > bufferSize) {
            buffer.removeAt(0)
        }
        callback?.invoke(entry)
    }

    fun getBuffer(): List<Entry> = buffer

    fun create(name: String, enabled: Boolean): LoggerInternal {
        val spi = spiFactory.invoke(name, enabled)
        return object : LoggerInternal {
            override fun isLoggable(level: Level): Boolean = spi.isLoggable(level)

            override fun log(level: Level, msg: String, caught: Throwable?) {
                add(Entry(Clock.System.now(), level, name, msg, caught))
                spi.log(level, msg, caught)
            }
        }
    }
}
