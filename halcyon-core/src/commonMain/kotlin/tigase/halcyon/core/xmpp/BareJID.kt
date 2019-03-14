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
package tigase.halcyon.core.xmpp

import kotlin.jvm.JvmStatic

data class BareJID constructor(val localpart: String? = null, val domain: String) {

	override fun toString(): String {
		return (when {
			localpart != null -> "$localpart@$domain"
			else -> domain
		})
	}

	companion object {
		@JvmStatic
		fun parse(jid: String): BareJID {
			val x = parseJID(jid)
			return BareJID(x[0], x[1]!!)
		}
	}
}

internal fun parseJID(jid: String): Array<String?> {
	val result = arrayOfNulls<String>(3)

	// Cut off the resource part first
	var idx = jid.indexOf('/')

	// Resource part:
	result[2] = if (idx == -1) null else jid.substring(idx + 1)

	val id = if (idx == -1) jid else jid.substring(0, idx)

	// Parse the localpart and the domain name
	idx = id.indexOf('@')
	result[0] = if (idx == -1) null else id.substring(0, idx)
	result[1] = if (idx == -1) id else id.substring(idx + 1)

	return result
}

fun String.toBareJID(): BareJID = BareJID.parse(this)