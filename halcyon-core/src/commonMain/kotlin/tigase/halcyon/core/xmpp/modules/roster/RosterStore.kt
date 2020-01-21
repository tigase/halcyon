package tigase.halcyon.core.xmpp.modules.roster

import tigase.halcyon.core.xmpp.modules.roster.RosterItem
import tigase.halcyon.core.xmpp.BareJID

interface RosterStore {

	fun getVersion(): String?
	fun setVersion(version: String)

	fun getItem(jid: BareJID): RosterItem?
	fun removeItem(jid: BareJID)
	fun addItem(jid: BareJID, value: RosterItem)
	fun updateItem(jid: BareJID, value: RosterItem)

	fun getAllItems(): List<RosterItem>
}