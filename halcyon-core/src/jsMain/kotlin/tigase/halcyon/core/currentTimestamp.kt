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
package tigase.halcyon.core

import tigase.halcyon.core.exceptions.HalcyonException
import kotlin.js.Date

actual fun currentTimestamp(): Long {
	return Date.now().toLong()
}

actual fun parseISO8601(date: String): Long {
	val rx =
		"^(\\d{4})-(\\d\\d)-(\\d\\d)([T ](\\d\\d):(\\d\\d):(\\d\\d)(\\.\\d+)?(Z|([+-])(\\d\\d)(:(\\d\\d))?)?)?\$".toRegex()
	val x = rx.find(date) ?: throw HalcyonException("Invalid ISO-8601 date.")

	val year = x.groupValues[1].toInt()
	val month = x.groupValues[2].toInt() - 1
	val day = x.groupValues[3].toInt()

	if (x.groupValues[4].isBlank()) {
		return Date.UTC(year, month, day).toLong()
	}

	var hour = x.groupValues[5].toInt()
	var minute = x.groupValues[6].toInt()
	val second = x.groupValues[7].toInt()
	val ms = x.groupValues[8].let { if (it.isNotEmpty()) "${it}000".substring(1, 4).toInt() else 0 }

	if (x.groupValues[9].isBlank()) {
		return Date.UTC(year, month, day, hour, minute, second, ms).toLong()
	}

	val oh = if (!x.groupValues[11].isBlank()) x.groupValues[11].toInt()
		.let { if (x.groupValues[10] == "-") -it else it } else 0
	val om = if (!x.groupValues[13].isBlank()) x.groupValues[13].toInt()
		.let { if (x.groupValues[10] == "-") -it else it } else 0

	hour -= oh
	minute -= om

	return Date.UTC(year, month, day, hour, minute, second, ms).toLong()
}

actual fun timestampToISO8601(timestamp: Long, utc: Boolean): String {
	return Date(timestamp).toISOString()
}