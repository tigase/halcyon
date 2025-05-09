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

import java.util.logging.LogRecord
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerInternal

actual class DefaultLoggerSPI actual constructor(name: String, val enabled: Boolean) :
    LoggerInternal {

    private val log = java.util.logging.Logger.getLogger(name)

    private fun cnv(level: Level): java.util.logging.Level = when (level) {
        Level.OFF -> java.util.logging.Level.OFF
        Level.SEVERE -> java.util.logging.Level.SEVERE
        Level.WARNING -> java.util.logging.Level.WARNING
        Level.INFO -> java.util.logging.Level.INFO
        Level.CONFIG -> java.util.logging.Level.CONFIG
        Level.FINE -> java.util.logging.Level.FINE
        Level.FINER -> java.util.logging.Level.FINER
        Level.FINEST -> java.util.logging.Level.FINEST
        Level.ALL -> java.util.logging.Level.ALL
    }

    actual override fun isLoggable(level: Level): Boolean = log.isLoggable(cnv(level))

    private fun doLog(level: Level, msg: String, caught: Throwable?) {
        if (!enabled) return
        val lr = LogRecord(cnv(level), msg)
        if (caught != null) lr.thrown = caught

        fillCaller(lr)

        log.log(lr)
    }

    private fun fillCaller(lr: LogRecord) {
        val trace = Throwable()
        val list = trace.stackTrace

        list.find { stackTraceElement ->
            !stackTraceElement.className.startsWith(
                "tigase.halcyon.core.logger."
            )
        }.let { stackTraceElement ->
            if (stackTraceElement != null) {
                lr.sourceClassName = stackTraceElement.className
                lr.sourceMethodName = stackTraceElement.methodName
            }
        }
    }

    actual override fun log(level: Level, msg: String, caught: Throwable?) {
        doLog(level, msg, caught)
    }
}
