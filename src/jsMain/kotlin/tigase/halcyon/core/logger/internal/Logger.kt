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
package tigase.halcyon.core.logger.internal

import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerSPI
import kotlin.js.Date

actual class DefaultLoggerSPI actual constructor(val name: String, val enabled: Boolean) : LoggerSPI {

	companion object {

		var levelFilter: Level = Level.INFO
		var nameFilter: String? = null
	}

	actual override fun isLoggable(level: Level): Boolean = levelFilter.value <= level.value

	private fun log(level: Level, msg: String) {
//		if (nameFilter != null && !name.matches(nameFilter!!)) {
//			return
//		}
		if (!enabled) return
		val dt = Date()
		val formattedMsg = "${dt.toUTCString()} [$level] $name: $msg"

		if (isLoggable(level)) when (level) {
			Level.SEVERE -> console.error(formattedMsg)
			Level.WARNING -> console.warn(formattedMsg)
			Level.INFO -> console.info(formattedMsg)
			Level.CONFIG -> console.info(formattedMsg)
			Level.FINE, Level.FINER, Level.FINEST -> console.log(formattedMsg)
			else -> {
			}
		}
	}

	actual override fun log(level: Level, msg: String, caught: Throwable?) {
		if (caught == null) log(level, msg) else log(level, msg + '\n' + caught.toString())
	}

}