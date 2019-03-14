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

actual class Logger actual constructor(name: String) {

	var level: tigase.halcyon.core.logger.Level = tigase.halcyon.core.logger.Level.INFO

	actual inline fun isLoggable(level: tigase.halcyon.core.logger.Level): Boolean = this.level.value >= level.value

	actual inline fun log(level: tigase.halcyon.core.logger.Level, msg: String) {
		if (isLoggable(level)) when (level) {
			tigase.halcyon.core.logger.Level.SEVERE -> console.error(msg)
			tigase.halcyon.core.logger.Level.WARNING -> console.warn(msg)
			tigase.halcyon.core.logger.Level.INFO -> console.info(msg)
			tigase.halcyon.core.logger.Level.CONFIG -> console.info(msg)
			tigase.halcyon.core.logger.Level.FINE, tigase.halcyon.core.logger.Level.FINER, tigase.halcyon.core.logger.Level.FINEST -> console.log(
				msg
			)
			else -> {
			}
		}
	}

	actual fun log(level: tigase.halcyon.core.logger.Level, msg: String, caught: Throwable) {
		log(level, msg + '\n' + caught.toString())
	}

	actual fun fine(msg: String) {
		log(tigase.halcyon.core.logger.Level.FINE, msg)
	}

	actual fun finer(msg: String) {
		log(tigase.halcyon.core.logger.Level.FINER, msg)
	}

	actual fun finest(msg: String) {
		log(tigase.halcyon.core.logger.Level.FINEST, msg)
	}

	actual fun config(msg: String) {
		log(tigase.halcyon.core.logger.Level.CONFIG, msg)
	}

	actual fun info(msg: String) {
		log(tigase.halcyon.core.logger.Level.INFO, msg)
	}

	actual fun warning(msg: String) {
		log(tigase.halcyon.core.logger.Level.WARNING, msg)
	}

	actual fun severe(msg: String) {
		log(tigase.halcyon.core.logger.Level.SEVERE, msg)
	}

}