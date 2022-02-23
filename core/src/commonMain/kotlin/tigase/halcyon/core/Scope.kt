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

import tigase.halcyon.core.eventbus.Event

enum class Scope {

	/**
	 * Properties in this scope are cleared when server sends new stream.
	 */
	Stream,

	/**
	 * Properties in this scope are cleared when connector is disconnected.
	 */
	Connection,

	/**
	 * Properties in this scope are cleared when client is manually stopped.
	 */
	Session,

	/**
	 * User property, as password, username etc. Not cleared.
	 */
	User,
}

data class ClearedEvent(val scopes: Array<Scope>) : Event(TYPE) { companion object {

	const val TYPE = "tigase.halcyon.core.ClearedEvent"
}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as ClearedEvent

		if (!scopes.contentEquals(other.scopes)) return false

		return true
	}

	override fun hashCode(): Int {
		return scopes.contentHashCode()
	}
}