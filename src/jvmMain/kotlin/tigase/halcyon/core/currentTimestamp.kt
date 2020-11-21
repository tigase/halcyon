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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

actual fun currentTimestamp(): Long {
	return System.currentTimeMillis()
}

actual fun timestampToISO8601(timestamp: Long, utc: Boolean): String {
	val c = Calendar.getInstance()!!.also { it.timeInMillis = timestamp }
	val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
	df.timeZone = TimeZone.getTimeZone("UTC")
	return df.format(c.time)
}

actual fun parseISO8601(date: String): Long {
	val r =
		"^(\\d{4})-(\\d\\d)-(\\d\\d)([T ](\\d\\d):(\\d\\d):(\\d\\d)(\\.\\d+)?(Z|([+-])(\\d\\d)(:(\\d\\d))?)?)?\$".toRegex()
	val mr = r.find(date) ?: throw HalcyonException("Invalid ISO-8601 date.")

	val year = mr.groupValues[1].toInt()
	val month = mr.groupValues[2].toInt() - 1
	val day = mr.groupValues[3].toInt()

	if (mr.groupValues[4].isEmpty()) {
		val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
		c.set(Calendar.YEAR, year)
		c.set(Calendar.MONTH, month)
		c.set(Calendar.DAY_OF_MONTH, day)
		c.set(Calendar.HOUR_OF_DAY, 0)
		c.set(Calendar.MINUTE, 0)
		c.set(Calendar.SECOND, 0)
		c.set(Calendar.MILLISECOND, 0)


		return c.time.time
	}

	var hour = mr.groupValues[5].toInt()
	var minute = mr.groupValues[6].toInt()
	val second = mr.groupValues[7].toInt()
	val ms = mr.groupValues[8].let { if (it.isNotEmpty()) "${it}000".substring(1, 4).toInt() else 0 }

	if (mr.groupValues[9].isEmpty()) {
		val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
		c.set(Calendar.YEAR, year)
		c.set(Calendar.MONTH, month)
		c.set(Calendar.DAY_OF_MONTH, day)
		c.set(Calendar.HOUR_OF_DAY, hour)
		c.set(Calendar.MINUTE, minute)
		c.set(Calendar.SECOND, second)
		c.set(Calendar.MILLISECOND, ms)


		return c.time.time
	}

	val oh = if (mr.groupValues[11].isNotEmpty()) mr.groupValues[11].toInt()
		.let { if (mr.groupValues[10] == "-") -it else it } else 0
	val om = if (mr.groupValues[13].isNotEmpty()) mr.groupValues[13].toInt()
		.let { if (mr.groupValues[10] == "-") -it else it } else 0

	hour -= oh
	minute -= om

	val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
	c.set(Calendar.YEAR, year)
	c.set(Calendar.MONTH, month)
	c.set(Calendar.DAY_OF_MONTH, day)
	c.set(Calendar.HOUR_OF_DAY, hour)
	c.set(Calendar.MINUTE, minute)
	c.set(Calendar.SECOND, second)
	c.set(Calendar.MILLISECOND, ms)


	return c.time.time
}