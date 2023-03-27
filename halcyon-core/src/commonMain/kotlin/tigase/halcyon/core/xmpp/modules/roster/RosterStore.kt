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

/**
 *  Roster store keeps received roster items.
 */
interface RosterStore {

	/**
	 * Returns roster version saved in store.
	 */
	fun getVersion(): String?

	/**
	 * Saves received roster version.
	 * @param version version of received roster.
	 */
	fun setVersion(version: String)

	/**
	 * Returns roster item for given bare JID.
	 */
	fun getItem(jid: BareJID): RosterItem?

	/**
	 * Removes roster item identified by given bare JID from store.
	 */
	fun removeItem(jid: BareJID)

	/**
	 * Adds roster item to store.
	 * @param value roster item to add.
	 */
	fun addItem(value: RosterItem)

	/**
	 * Updates roster item (identified by [RosterItem.jid]) in store.
	 * @param value roster item to update.
	 */
	fun updateItem(value: RosterItem)

	/**
	 * Returns all roster items saved in store.
	 */
	fun getAllItems(): List<RosterItem>
}