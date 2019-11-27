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
package tigase.halcyon.core.logger

import kotlin.js.Date

actual class Logger actual constructor(val name: String) {

	companion object {
		var levelFilter: Level = Level.INFO
		var nameFilter: String? = null
	}

	actual fun isLoggable(level: Level): Boolean = levelFilter.value <= level.value

	actual fun log(level: Level, msg: String) {
//		if (nameFilter != null && !name.matches(nameFilter!!)) {
//			return
//		}
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

	actual fun log(level: Level, msg: String, caught: Throwable) {
		log(level, msg + '\n' + caught.toString())
	}

	actual fun fine(msg: String) {
		log(Level.FINE, msg)
	}

	actual fun finer(msg: String) {
		log(Level.FINER, msg)
	}

	actual fun finest(msg: String) {
		log(Level.FINEST, msg)
	}

	actual fun config(msg: String) {
		log(Level.CONFIG, msg)
	}

	actual fun info(msg: String) {
		log(Level.INFO, msg)
	}

	actual fun warning(msg: String) {
		log(Level.WARNING, msg)
	}

	actual fun severe(msg: String) {
		log(Level.SEVERE, msg)
	}

}