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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerInternal

actual class DefaultLoggerSPI actual constructor(val name: String, val enabled: Boolean) :
    LoggerInternal {

    companion object {

        var levelFilter: Level = Level.INFO
        var nameFilter: String? = null
    }
	
    actual override fun isLoggable(level: Level): Boolean = levelFilter.value <= level.value

    actual override fun log(level: Level, msg: String, caught: Throwable?) {
        if (!enabled) return

        val ts = Clock.System.now()
            .toLocalDateTime(TimeZone.UTC)
            .toString()
        println("$ts: $msg")
    }
}
