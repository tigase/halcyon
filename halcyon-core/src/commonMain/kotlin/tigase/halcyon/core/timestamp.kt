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

import kotlinx.datetime.*
import tigase.halcyon.core.exceptions.HalcyonException

fun timestampToISO8601(timestamp: Instant): String = buildString {
	timestamp.toLocalDateTime(TimeZone.UTC)
		.let {
			append(
				it.year.toString()
					.padStart(4, '0')
			)
			append("-")
			append(
				it.monthNumber.toString()
					.padStart(2, '0')
			)
			append("-")
			append(
				it.dayOfMonth.toString()
					.padStart(2, '0')
			)
			append("T")
			append(
				it.hour.toString()
					.padStart(2, '0')
			)
			append(":")
			append(
				it.minute.toString()
					.padStart(2, '0')
			)
			append(":")
			append(
				it.second.toString()
					.padStart(2, '0')
			)
			append(".")
			append(
				(it.nanosecond / 1000000).toString()
					.padStart(3, '0')
			)
			append("Z")
		}
}

fun parseISO8601(date: String): Instant {
	val rx =
		"^(\\d{4})-(\\d\\d)-(\\d\\d)([T ](\\d\\d):(\\d\\d):(\\d\\d)(\\.\\d+)?(Z|([+-])(\\d\\d)(:(\\d\\d))?)?)?\$".toRegex()
	val x = rx.find(date) ?: throw HalcyonException("Invalid ISO-8601 date.")

	val year = x.groupValues[1].toInt()
	val month = x.groupValues[2].toInt() - 1
	val day = x.groupValues[3].toInt()


	if (x.groupValues[4].isBlank()) {
		return LocalDate(year, Month.values()[month], day).atStartOfDayIn(TimeZone.UTC)
	}

	val hour = x.groupValues[5].toInt()
	val minute = x.groupValues[6].toInt()
	val second = x.groupValues[7].toInt()
	val ms = x.groupValues[8].let {
		if (it.isNotEmpty()) "${it}000".substring(1, 4)
			.toInt() else 0
	}

	if (x.groupValues[9].isBlank()) {
		return LocalDateTime(year, Month.values()[month], day, hour, minute, second, ms).toInstant(TimeZone.UTC)
	}

	return Instant.parse(date)
}

fun String.fromISO8601(): Instant = parseISO8601(this)