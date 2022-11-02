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
package tigase.halcyon.core.xmpp.modules.roster

import tigase.halcyon.core.xmpp.BareJID

class InMemoryRosterStore : RosterStore {

	private var version: String? = null

	private val items = mutableMapOf<BareJID, RosterItem>()

	override fun getVersion(): String? = this.version

	override fun setVersion(version: String) {
		this.version = version
	}

	override fun getItem(jid: BareJID): RosterItem? = this.items[jid]

	override fun removeItem(jid: BareJID) {
		this.items.remove(jid)
	}

	override fun addItem(jid: BareJID, value: RosterItem) {
		this.items[jid] = value
	}

	override fun updateItem(jid: BareJID, value: RosterItem) {
		this.items[jid] = value
	}

	override fun getAllItems(): List<RosterItem> = items.values.toList()
}