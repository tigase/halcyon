package tigase.halcyon.core.xmpp.modules.roster

import tigase.halcyon.core.xmpp.BareJID

class DefaultRosterStore : RosterStore {

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