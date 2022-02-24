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
package tigase.halcyon.core.logger

import tigase.halcyon.core.logger.internal.DefaultLoggerSPI

/**
 * Service Provider Interface.
 */
interface LoggerSPI {

	fun isLoggable(level: Level): Boolean
	fun log(level: Level, msg: String, caught: Throwable?)

}

object LoggerFactory {

	var spiFactory: ((String, Boolean) -> LoggerSPI) = { name, enabled -> DefaultLoggerSPI(name, enabled) }

	fun logger(name: String, enabled: Boolean = true): Logger {
		return SimpleLogger(spiFactory.invoke(name, enabled))
	}

}

interface Logger {

	fun isLoggable(level: Level): Boolean
	fun log(level: Level, msg: String)
	fun log(level: Level, msg: String, caught: Throwable)

	fun fine(msg: String) = log(Level.FINE, msg)
	fun finer(msg: String) = log(Level.FINER, msg)
	fun finest(msg: String) = log(Level.FINEST, msg)

	fun config(msg: String) = log(Level.CONFIG, msg)
	fun info(msg: String) = log(Level.INFO, msg)
	fun warning(msg: String) = log(Level.WARNING, msg)
	fun severe(msg: String) = log(Level.SEVERE, msg)

	fun log(level: Level, caught: Throwable? = null, msg: () -> Any?) {
		if (isLoggable(level)) {
			if (caught == null) log(level, msg.invoke().toString())
			else log(level, msg.invoke().toString(), caught)
		}
	}

	fun fine(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.FINE, caught = caught, msg = msg)
	fun finer(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.FINER, caught = caught, msg = msg)
	fun finest(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.FINEST, caught = caught, msg = msg)

	fun config(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.CONFIG, caught = caught, msg = msg)
	fun info(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.INFO, caught = caught, msg = msg)
	fun warning(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.WARNING, caught = caught, msg = msg)
	fun severe(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.SEVERE, caught = caught, msg = msg)
}

class SimpleLogger(private val spi: LoggerSPI) : Logger {

	override fun isLoggable(level: Level): Boolean = spi.isLoggable(level)

	override fun log(level: Level, msg: String) = spi.log(level, msg, null)

	override fun log(level: Level, msg: String, caught: Throwable) = spi.log(level, msg, caught)

}