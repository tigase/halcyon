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

import getFromAttr
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.stanzas.Presence
import tigase.halcyon.core.xmpp.stanzas.PresenceType
import tigase.halcyon.core.xmpp.stanzas.presence
import tigase.halcyon.core.xmpp.stanzas.wrap

data class PresenceReceivedEvent(val jid: JID, val stanzaType: PresenceType?, val stanza: Presence) :
	Event(TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.xmpp.modules.presence.PresenceReceivedEvent"
	}
}

class PresenceModule : XmppModule {

	private val log = Logger("tigase.halcyon.core.xmpp.modules.presence.PresenceModule")

	override val type = TYPE
	override lateinit var context: tigase.halcyon.core.Context
	override val criteria = Criterion.name(Presence.NAME)
	override val features: Array<String>? = null

	companion object {
		const val TYPE = "PresenceModule"
	}

	override fun initialize() {
	}

	override fun process(element: Element) {
		val presence: Presence = wrap(element)
		val fromJID = presence.getFromAttr()
		log.finest("Presence received from $fromJID :: ${presence.getAsString()}")
		if (fromJID == null) {
			return
		}
		context.eventBus.fire(PresenceReceivedEvent(fromJID, presence.type, presence))
	}

	fun sendInitialPresence() {
		val presence = presence { }
		context.writer.writeDirectly(presence)
	}

}