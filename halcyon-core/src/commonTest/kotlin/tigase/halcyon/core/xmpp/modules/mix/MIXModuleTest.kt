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
package tigase.halcyon.core.xmpp.modules.mix

import kotlin.test.Test
import kotlin.test.assertFalse
import tigase.DummyHalcyon
import tigase.halcyon.core.xmpp.modules.roster.RosterItem
import tigase.halcyon.core.xmpp.modules.roster.RosterModule
import tigase.halcyon.core.xmpp.stanzas.message
import tigase.halcyon.core.xmpp.toBareJID
import tigase.halcyon.core.xmpp.toJID

class MIXModuleTest {

    val halcyon = DummyHalcyon().apply {
        connect()
    }

    /**
     * Problem HALCYON-51
     */
    @Test
    fun testMIXMessageEventCalling() {
        halcyon.getModule<RosterModule>(RosterModule.TYPE).store.addItem(
            RosterItem(
                "arturs@mix.tigase.org".toBareJID(),
                "MIX",
                annotations = arrayOf(
                    MIXRosterItemAnnotation("123")
                )
            )
        )

        val module = halcyon.getModule<MIXModule>(MIXModule.TYPE)

        var eventCalled = false

        halcyon.eventBus.register<MIXMessageEvent>(MIXMessageEvent.TYPE) {
            eventCalled = true
        }

        val stanza = message {
            from = "arturs@mix.tigase.org".toJID()
            to = "kobit@tigase.org".toJID()
            attributes["id"] = "4"
            "event" {
                xmlns = "http://jabber.org/protocol/pubsub#event"
                "items" {
                    attributes["node"] = "urn:xmpp:mix:nodes:participants"
                }
            }
            "stanza-id" {
                xmlns = "urn:xmpp:sid:0"
                attributes["id"] = "2b40219a-8f51-419f-ab0f-71ee03d278e9"
                attributes["by"] = "kobit@tigase.org"
            }
        }

        if (module.criteria.match(stanza)) module.process(stanza)

        assertFalse(stanza.isMixMessage(), "This is not MIX Message")
        assertFalse(eventCalled, "Event should not be called")
    }
}
