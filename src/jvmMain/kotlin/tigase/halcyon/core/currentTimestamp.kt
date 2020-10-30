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
		"^(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)T(\\d\\d):(\\d\\d):(\\d\\d)(\\.(\\d+))?(([+-]\\d\\d:?\\d\\d)|Z)?$".toRegex()
	val mr = r.find(date) ?: throw HalcyonException("Invalid ISO-8601 date.")
	val yyyy: Int = mr.groupValues[1].toInt()
	val MM: Int = mr.groupValues[2].toInt()
	val dd: Int = mr.groupValues[3].toInt()
	val hh: Int = mr.groupValues[4].toInt()
	val mm: Int = mr.groupValues[5].toInt()
	val ss: Int = mr.groupValues[6].toInt()
	val ms: Int = mr.groupValues[8].let { if (it.isNotEmpty()) it.toInt() else 0 }
	val tzValue: String = mr.groupValues[9]

	return Calendar.getInstance(if (tzValue == "Z") TimeZone.getTimeZone("UTC") else TimeZone.getTimeZone("GMT$tzValue"))
		.also {
			it.clear()
			it.set(yyyy, MM - 1, dd, hh, mm, ss)
			it.set(Calendar.MILLISECOND, ms)
		}.timeInMillis
}