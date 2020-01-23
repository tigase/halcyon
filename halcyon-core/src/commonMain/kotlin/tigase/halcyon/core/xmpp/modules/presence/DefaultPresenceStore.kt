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
package tigase.halcyon.core.xmpp.modules.presence

import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.stanzas.Presence

class DefaultPresenceStore : PresenceStore {

	private val presences = mutableMapOf<JID, Presence>()

	override fun setPresence(stanza: Presence) {
		val jid = stanza.from ?: return
		presences[jid] = stanza
	}

	override fun getPresence(jid: JID): Presence? {
		return presences[jid]
	}

	override fun removePresence(jid: JID): Presence? {
		return presences.remove(jid)
	}

	override fun getPresences(jid: BareJID): List<Presence> {
		return presences.values.filter { presence -> presence.from?.bareJID == jid }
	}

}