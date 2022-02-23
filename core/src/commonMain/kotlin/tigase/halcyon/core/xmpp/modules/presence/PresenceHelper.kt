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
package tigase.halcyon.core.xmpp.modules.presence

import tigase.halcyon.core.xmpp.stanzas.Presence
import tigase.halcyon.core.xmpp.stanzas.PresenceType
import tigase.halcyon.core.xmpp.stanzas.Show

enum class TypeAndShow {

	/**
	 * The entity or resource is actively interested in chatting.
	 */
	Chat,

	/**
	 * The entity or resource is online.
	 */
	Online,

	/**
	 * The entity or resource is busy (dnd = "Do Not Disturb").
	 */
	Dnd,

	/**
	 * The entity or resource is temporarily away.
	 */
	Away,

	/**
	 * The entity or resource is away for an extended period (xa =
	 * "eXtended Away").
	 */
	Xa,

	/**
	 * The entity or resource is offline.
	 */
	Offline,

	/**
	 * Server returns error instead of presence of entity or resource.
	 */
	Error,

	/**
	 * Type and Show cannot be calculated.
	 */
	Unknown
}

/**
 * Calculate logical status of presence based on stanza type and presence show field.
 */
fun Presence?.typeAndShow(): TypeAndShow {
	if (this == null) return TypeAndShow.Offline
	val type = this.type
	val show = this.show
	return when (type) {
		PresenceType.Error -> TypeAndShow.Error
		PresenceType.Unavailable -> TypeAndShow.Offline
		null -> when (show) {
			null -> TypeAndShow.Online
			Show.XA -> TypeAndShow.Xa
			Show.DnD -> TypeAndShow.Dnd
			Show.Away -> TypeAndShow.Away
			Show.Chat -> TypeAndShow.Chat
		}
		else -> TypeAndShow.Unknown
	}
}