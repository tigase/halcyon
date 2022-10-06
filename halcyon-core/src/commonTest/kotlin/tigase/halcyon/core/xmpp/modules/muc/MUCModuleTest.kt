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
package tigase.halcyon.core.xmpp.modules.muc

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SessionController
import tigase.halcyon.core.connector.State
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.parser.parseXML
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.modules.MessageReceivedEvent
import tigase.halcyon.core.xmpp.modules.PingModule
import tigase.halcyon.core.xmpp.stanzas.*
import tigase.halcyon.core.xmpp.toBareJID
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.*

class MockConnector(halcyon: AbstractHalcyon, val sentElements: MutableList<Element>) : AbstractConnector(halcyon) {

	override fun createSessionController(): SessionController {
		return object : SessionController {
			override val halcyon: AbstractHalcyon
				get() = TODO("Not yet implemented")

			override fun start() {
			}

			override fun stop() {
			}
		}
	}

	override fun send(data: CharSequence) {
		try {
			val pr = parseXML(data.toString())
			pr.element?.let {
				sentElements.add(it)
			}
		} catch (ignore: Throwable) {
		}
	}

	override fun start() {
		state = State.Connected
	}

	override fun stop() {
		state = State.Disconnected
	}
}

fun assertContains(expected: Element, actual: Element, message: String? = null) {
	fun check(expected: Element, actual: Element): Boolean {
		if (expected.name != actual.name) return false
		if (expected.value != null && expected.value != actual.value) return false
		if (!expected.attributes.filter { it.key != "id" }
				.all { e -> actual.attributes[e.key] == e.value }) return false
		if (!expected.children.all { e ->
				actual.children.any { a -> check(e, a) }
			}) return false
		return true
	}

	fun messagePrefix(message: String?) = if (message == null) "" else "$message. "

	if (!check(expected, actual)) {
		fail(messagePrefix(message) + "Expected all of ${expected.getAsString()}, actual ${actual.getAsString()}.")
	}
}

class MUCModuleTest {

	lateinit var halcyon: AbstractHalcyon
	lateinit var sentElements: MutableList<Element>

	@BeforeTest
	fun setUpHalcyon() {
		val tmp: MutableList<Element> = mutableListOf()
		sentElements = tmp
		halcyon = object : AbstractHalcyon() {

			override fun reconnect(immediately: Boolean) = TODO("not implemented")
			override fun createConnector(): AbstractConnector = MockConnector(this, tmp)
		}
		halcyon.connect()
	}

	private fun processReceived(stanza: Element) {
		halcyon.eventBus.fire(ReceivedXMLElementEvent(stanza))
	}

	@Test
	fun Join_to_MUC_room() {
		val events = mutableListOf<Event>()
		halcyon.eventBus.register<Event> { events.add(it) }
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		muc.join("coven@chat.shakespeare.lit".toBareJID(), "thirdwitch")
			.send()
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			"x" { xmlns = "http://jabber.org/protocol/muc" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		val rp = presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "member"
					attributes["role"] = "participant"
				}
				"status" { attributes["code"] = "110" }
				"status" { attributes["code"] = "201" }
			}
		}

		assertTrue(muc.criteria.match(rp), "Sender is not identified as MUC Room")
		muc.process(rp)

		val room = assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID()), "Room must be there!")

		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Room state is not changed")
		assertTrue(events.any { event -> event is MucRoomEvents.YouJoined }, "Event is not fired")
		assertEquals("thirdwitch", room.nickname, "Invalid nickname")
		val occupant = assertNotNull(room.occupants()["thirdwitch"])
		assertEquals(rp, occupant.presence)

		assertEquals(Role.Participant, occupant.role)
		assertEquals(Affiliation.Member, occupant.affiliation)

		assertTrue(events.any { event -> event is MucRoomEvents.Created }, "RoomCreated event missing")

		muc.leave(room)
			.send()
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			type = PresenceType.Unavailable
		}, sentElements.removeLast(), "Invalid output stanza,")

		presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			type = PresenceType.Unavailable
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "member"
					attributes["role"] = "none"
				}
				"status" { attributes["code"] = "110" }
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.NotJoined, room.state, "Room state is not changed")
		assertTrue(events.any { event -> event is MucRoomEvents.YouLeaved }, "Event is not fired")
	}

	@Test
	fun Join_to_MUC_room_with_occupant() {
		val events = mutableListOf<Event>()
		halcyon.eventBus.register<Event> { events.add(it) }
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		muc.join("coven@chat.shakespeare.lit".toBareJID(), "thirdwitch")
			.send()

		// occupant join
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			"x" { xmlns = "http://jabber.org/protocol/muc" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		// Other occupant join
		presence {
			from = "coven@chat.shakespeare.lit/firstwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "owner"
					attributes["role"] = "moderator"
				}
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		val room = assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID()), "Room must be there!")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.RequestSent, room.state, "Room state changed")
		assertTrue(events.any { event -> event is MucRoomEvents.OccupantCame && event.nickname == "firstwitch" },
				   "Event OccupantCame is not fired"
		)

		assertEquals(1, room.occupants().size)
		assertNotNull(room.occupants()["firstwitch"]).let {
			assertEquals(Role.Moderator, it.role)
			assertEquals(Affiliation.Owner, it.affiliation)
			assertNull(it.presence.show)
		}

		// own presence

		presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "member"
					attributes["role"] = "participant"
				}
				"status" { attributes["code"] = "110" }
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Room state is not changed")
		assertTrue(events.any { event -> event is MucRoomEvents.YouJoined }, "Event is not fired")
		assertEquals(2, room.occupants().size)

		// first witch changes presence
		presence {
			from = "coven@chat.shakespeare.lit/firstwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			show = Show.DnD
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "owner"
					attributes["role"] = "moderator"
				}
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}
		assertNotNull(room.occupants()["firstwitch"]).let {
			assertEquals(Role.Moderator, it.role)
			assertEquals(Affiliation.Owner, it.affiliation)
			assertEquals(Show.DnD, it.presence.show)
		}
		assertTrue(events.any { event -> event is MucRoomEvents.OccupantChangedPresence && event.nickname == "firstwitch" },
				   "OccupantChangedPresence event is not fired"
		)

		// firstwitch leaves room

		presence {
			from = "coven@chat.shakespeare.lit/firstwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			type = PresenceType.Unavailable
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "owner"
					attributes["role"] = "none"
				}
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Room state is not changed")
		assertTrue(events.any { event -> event is MucRoomEvents.OccupantLeave && event.nickname == "firstwitch" },
				   "OccupantLeave event is not fired"
		)
		assertEquals(1, room.occupants().size)
	}

	@Test
	fun Join_to_MUC_room_with_occupant_receiving_occupant_kick() {
		val events = mutableListOf<Event>()
		halcyon.eventBus.register<Event> { events.add(it) }
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		muc.join("coven@chat.shakespeare.lit".toBareJID(), "thirdwitch")
			.send()

		// occupant join
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			"x" { xmlns = "http://jabber.org/protocol/muc" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		// Other occupant join
		presence {
			from = "coven@chat.shakespeare.lit/firstwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "owner"
					attributes["role"] = "moderator"
				}
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		val room = assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID()), "Room must be there!")

		presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "member"
					attributes["role"] = "participant"
				}
				"status" { attributes["code"] = "110" }
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Room state is not changed")
		assertEquals(2, room.occupants().size)

		// firstwitch is kicked
		presence {
			from = "coven@chat.shakespeare.lit/firstwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			type = PresenceType.Unavailable
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "none"
					attributes["role"] = "none"
				}
				"status" { attributes["code"] = "307" }
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		assertTrue(events.any { event -> event is MucRoomEvents.OccupantLeave && event.nickname == "firstwitch" },
				   "OccupantChangedPresence event is not fired"
		)
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Invalid room state")
		assertEquals(1, room.occupants().size)
	}

	@Test
	fun Join_to_MUC_room_error() {
		val events = mutableListOf<Event>()
		halcyon.eventBus.register<Event> { events.add(it) }
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		muc.join("coven@chat.shakespeare.lit".toBareJID(), "thirdwitch")
			.send()
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			"x" { xmlns = "http://jabber.org/protocol/muc" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		val rp = presence {
			from = "coven@chat.shakespeare.lit".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			type = PresenceType.Error
			"error" {
				attributes["by"] = "coven@chat.shakespeare.lit"
				attributes["type"] = "modify"
				"jid-malformed" {
					xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
				}
			}
		}

		assertTrue(muc.criteria.match(rp), "Sender is not identified as MUC Room")
		muc.process(rp)

		val room = assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID()), "Room must be there!")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.NotJoined, room.state, "Room state is not changed")
		assertTrue(
			events.any { event -> event is MucRoomEvents.JoinError && event.condition == ErrorCondition.JidMalformed },
			"JoinError event missing"
		)
	}

	@Test
	fun Join_to_MUC_room_and_be_kicked() {
		val events = mutableListOf<Event>()
		halcyon.eventBus.register<Event> { events.add(it) }
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		muc.join("coven@chat.shakespeare.lit".toBareJID(), "thirdwitch")
			.send()
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			"x" { xmlns = "http://jabber.org/protocol/muc" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "member"
					attributes["role"] = "participant"
				}
				"status" { attributes["code"] = "110" }
				"status" { attributes["code"] = "201" }
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		val room = assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID()), "Room must be there!")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Room state is not changed")

		// receiving kick
		presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			type = PresenceType.Unavailable
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "none"
					attributes["role"] = "none"
					"actor" {
						attributes["nick"] = "Fluellen"
					}
					"reason" {
						+"Avaunt, you cullion!"
					}
				}
				"status" { attributes["code"] = "110" }
				"status" { attributes["code"] = "307" }
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		assertTrue(events.any { event -> event is MucRoomEvents.YouLeaved }, "YouLeaved event missing")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.NotJoined, room.state, "Room state is not changed")
		assertEquals(0, room.occupants().size)
	}

	@Test
	fun Join_to_MUC_room_and_be_banned() {
		val events = mutableListOf<Event>()
		halcyon.eventBus.register<Event> { events.add(it) }
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		muc.join("coven@chat.shakespeare.lit".toBareJID(), "thirdwitch")
			.send()
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			"x" { xmlns = "http://jabber.org/protocol/muc" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "member"
					attributes["role"] = "participant"
				}
				"status" { attributes["code"] = "110" }
				"status" { attributes["code"] = "201" }
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		val room = assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID()), "Room must be there!")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Room state is not changed")

		// receiving kick
		presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			type = PresenceType.Unavailable
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "outcast"
					attributes["role"] = "none"
					"actor" { attributes["nick"] = "Fluellen" }
					"reason" { +"Treason" }
				}
//				"status"{ attributes["code"] = "110" }
				"status" { attributes["code"] = "301" }
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		assertTrue(events.any { event -> event is MucRoomEvents.YouLeaved }, "YouLeaved event missing")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.NotJoined, room.state, "Room state is not changed")
		assertEquals(0, room.occupants().size)
	}

	@Test
	fun Join_and_send_message() {
		val events = mutableListOf<Event>()
		halcyon.eventBus.register<Event> { events.add(it) }
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		muc.join("coven@chat.shakespeare.lit".toBareJID(), "thirdwitch")
			.send()
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			"x" { xmlns = "http://jabber.org/protocol/muc" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "member"
					attributes["role"] = "participant"
				}
				"status" { attributes["code"] = "110" }
				"status" { attributes["code"] = "201" }
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		val room = assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID()), "Room must be there!")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Room state is not changed")

		muc.message(room, "Thrice and once the hedge-pig whined.")
			.send()
		assertContains(message {
			type = MessageType.Groupchat
			to = "coven@chat.shakespeare.lit".toJID()
			"body" { +"Thrice and once the hedge-pig whined." }
		}, sentElements.removeLast(), "Invalid output message stanza")

		message {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			type = MessageType.Groupchat
			"body" { +"Thrice and once the hedge-pig whined." }
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		assertTrue(
			events.any { event -> event is MucRoomEvents.ReceivedMessage && event.nickname == "thirdwitch" && event.message.body == "Thrice and once the hedge-pig whined." },
			"Message event missing"
		)
	}

	@Test
	fun Join_and_receive_direct_message() {
		val events = mutableListOf<Event>()
		halcyon.eventBus.register<Event> { events.add(it) }
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		muc.join("coven@chat.shakespeare.lit".toBareJID(), "thirdwitch")
			.send()

		var receivedEvent: MessageReceivedEvent? = null

		halcyon.eventBus.register<MessageReceivedEvent>(MessageReceivedEvent.TYPE) { receivedEvent = it }

		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			"x" { xmlns = "http://jabber.org/protocol/muc" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		processReceived(presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "member"
					attributes["role"] = "participant"
				}
				"status" { attributes["code"] = "110" }
				"status" { attributes["code"] = "201" }
			}
		})

		val room = assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID()), "Room must be there!")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Room state is not changed")

		processReceived(message {
			from = "coven@chat.shakespeare.lit/secondwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			type = MessageType.Chat
			"body" { +"Thrice and once the hedge-pig whined." }
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
			}
		})

		assertNotNull(receivedEvent).let {
			assertEquals("coven@chat.shakespeare.lit/secondwitch".toJID(), it.fromJID)
			assertEquals("Thrice and once the hedge-pig whined.", it.stanza.body)
		}
	}

	@Test
	fun Join_and_destroy() {
		val events = mutableListOf<Event>()
		halcyon.eventBus.register<Event> { events.add(it) }
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		muc.join("coven@chat.shakespeare.lit".toBareJID(), "thirdwitch")
			.send()
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			"x" { xmlns = "http://jabber.org/protocol/muc" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "member"
					attributes["role"] = "participant"
				}
				"status" { attributes["code"] = "110" }
				"status" { attributes["code"] = "201" }
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		val room = assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID()), "Room must be there!")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Room state is not changed")

		muc.destroy(room)
			.send()
		assertContains(iq {
			type = IQType.Set
			to = "coven@chat.shakespeare.lit".toJID()
			"query" {
				xmlns = "http://jabber.org/protocol/muc#owner"
				"destroy" {}
			}
		}, sentElements.removeLast(), "Invalid output message stanza")

		presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			type = PresenceType.Unavailable
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "none"
					attributes["role"] = "none"
				}
				"destroy" {
					"reason" { +"Macbeth doth come." }
				}
			}
		}.let {
			assertTrue(muc.criteria.match(it), "Sender is not identified as MUC Room")
			muc.process(it)
		}

		assertTrue(events.any { event -> event is MucRoomEvents.YouLeaved }, "Message event missing")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.NotJoined, room.state, "Room state is not changed")
	}

	private fun createRoomAndJoin(muc: MUCModule): Room {
		muc.join("coven@chat.shakespeare.lit".toBareJID(), "thirdwitch")
			.send()
		halcyon.eventBus.register<MucRoomEvents>(MucRoomEvents.TYPE) {
			when (it) {
				is MucRoomEvents.YouJoined -> println("You joined to room ${it.room.roomJID} as ${it.nickname}")
				is MucRoomEvents.JoinError -> println("Oh! You cannot join to room ${it.room.roomJID} because of ${it.condition}")
				else -> {}
			}
		}
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		processReceived(presence {
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			to = "hag66@shakespeare.lit/pda".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"item" {
					attributes["affiliation"] = "member"
					attributes["role"] = "participant"
				}
				"status" { attributes["code"] = "110" }
				"status" { attributes["code"] = "201" }
			}
		})
		val room = assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID()), "Room must be there!")
		assertEquals(tigase.halcyon.core.xmpp.modules.muc.State.Joined, room.state, "Room state is not changed")
		return room
	}

	@Test
	fun Join_and_send_mediated_invitation() {
		val events = mutableListOf<Event>()
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		halcyon.eventBus.register<Event> { events.add(it) }
		val room = createRoomAndJoin(muc)

		muc.invite(room, "hecate@shakespeare.lit".toBareJID(), "Hey Hecate")
			.send()
		assertContains(message {
			to = "coven@chat.shakespeare.lit".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"invite" {
					attributes["to"] = "hecate@shakespeare.lit"
					"reason" { +"Hey Hecate" }
				}
			}
		}, sentElements.removeLast(), "Invalid output message stanza")
	}

	@Test
	fun Join_and_send_direct_invitation() {
		val events = mutableListOf<Event>()
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		halcyon.eventBus.register<Event> { events.add(it) }
		val room = createRoomAndJoin(muc)

		muc.inviteDirectly(room, "hecate@shakespeare.lit".toBareJID(), "Hey Hecate")
			.send()
		assertContains(message {
			to = "hecate@shakespeare.lit".toJID()
			"x" {
				xmlns = "jabber:x:conference"
				attributes["jid"] = "coven@chat.shakespeare.lit"
				attributes["reason"] = "Hey Hecate"
			}
		}, sentElements.removeLast(), "Invalid output message stanza")
	}

	@Test
	fun received_mediated_invitation_and_join() {
		val events = mutableListOf<Event>()
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		halcyon.eventBus.register<Event> { events.add(it) }

		halcyon.eventBus.register<MucRoomEvents>(MucRoomEvents.TYPE) { event ->
			when (event) {
				is MucRoomEvents.YouJoined -> println("You joined to room ${event.room.roomJID}")
				is MucRoomEvents.OccupantCame -> println("Occupant ${event.nickname} came to ${event.room.roomJID}")
				is MucRoomEvents.OccupantLeave -> println("Occupant ${event.nickname} leaves room ${event.room.roomJID}")
				else -> {}
			}
		}

		processReceived(message {
			from = "coven@chat.shakespeare.lit".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"invite" {
					attributes["from"] = "crone1@shakespeare.lit/desktop"
					"reason" { +"Hey" }
				}
				"password" { +"123" }
			}
		})

		val invitation =
			(events.firstOrNull { event -> event is MucEvents.InvitationReceived } as MucEvents.InvitationReceived?)?.invitation
				?: fail("InvitationReceivedEvent not received")

		assertEquals("123", invitation.password)
		assertEquals("Hey", invitation.reason)
		assertEquals("coven@chat.shakespeare.lit".toBareJID(), invitation.roomjid)
		assertEquals("crone1@shakespeare.lit/desktop".toJID(), invitation.sender)

		muc.accept(invitation, "thirdwitch")
			.send()
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("coven@chat.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc"
				"password" { +"123" }
			}
		}, sentElements.removeLast(), "Invalid output stanza,")
	}

	@Test
	fun received_mediated_invitation_and_decline() {
		val events = mutableListOf<Event>()
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		halcyon.eventBus.register<Event> { events.add(it) }

		processReceived(message {
			from = "coven@chat.shakespeare.lit".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"invite" {
					attributes["from"] = "crone1@shakespeare.lit/desktop"
					"reason" { +"Hey" }
				}
				"password" { +"123" }
			}
		})

		val invitation =
			(events.firstOrNull { event -> event is MucEvents.InvitationReceived } as MucEvents.InvitationReceived?)?.invitation
				?: fail("InvitationReceivedEvent not received")

		assertEquals("123", invitation.password)
		assertEquals("Hey", invitation.reason)
		assertEquals("coven@chat.shakespeare.lit".toBareJID(), invitation.roomjid)
		assertEquals("crone1@shakespeare.lit/desktop".toJID(), invitation.sender)

		muc.decline(invitation, "Sorry")
			.send()

		assertContains(message {
			to = "coven@chat.shakespeare.lit".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc#user"
				"decline" {
					attributes["to"] = "crone1@shakespeare.lit"
					"reason" { +"Sorry" }
				}
			}
		}, sentElements.removeLast(), "Invalid output message stanza")
	}

	@Test
	fun received_direct_invitation_and_decline() {
		val events = mutableListOf<Event>()
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		halcyon.eventBus.register<Event> { events.add(it) }

		processReceived(message {
			from = "crone1@shakespeare.lit/desktop".toJID()
			to = "hecate@shakespeare.lit".toJID()
			"x" {
				xmlns = "jabber:x:conference"
				attributes["jid"] = "darkcave@macbeth.shakespeare.lit"
				attributes["password"] = "cauldronburn"
				attributes["reason"] = "Hey Hecate, this is the place for all good witches!"
			}
		})

		val invitation =
			(events.firstOrNull { event -> event is MucEvents.InvitationReceived } as MucEvents.InvitationReceived?)?.invitation
				?: fail("InvitationReceivedEvent not received")

		assertEquals("cauldronburn", invitation.password)
		assertEquals("Hey Hecate, this is the place for all good witches!", invitation.reason)
		assertEquals("darkcave@macbeth.shakespeare.lit".toBareJID(), invitation.roomjid)
		assertEquals("crone1@shakespeare.lit/desktop".toJID(), invitation.sender)

		assertFailsWith<HalcyonException> {
			muc.decline(invitation, "Sorry")
				.send()
		}

		assertNull(sentElements.removeLastOrNull())

	}

	@Test
	fun received_direct_invitation_and_join() {
		val events = mutableListOf<Event>()
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		halcyon.eventBus.register<Event> { events.add(it) }

		processReceived(message {
			from = "crone1@shakespeare.lit/desktop".toJID()
			to = "hecate@shakespeare.lit".toJID()
			"x" {
				xmlns = "jabber:x:conference"
				attributes["jid"] = "darkcave@macbeth.shakespeare.lit"
				attributes["password"] = "cauldronburn"
				attributes["reason"] = "Hey Hecate, this is the place for all good witches!"
			}
		})

		val invitation =
			(events.firstOrNull { event -> event is MucEvents.InvitationReceived } as MucEvents.InvitationReceived?)?.invitation
				?: fail("InvitationReceivedEvent not received")

		assertEquals("cauldronburn", invitation.password)
		assertEquals("Hey Hecate, this is the place for all good witches!", invitation.reason)
		assertEquals("darkcave@macbeth.shakespeare.lit".toBareJID(), invitation.roomjid)
		assertEquals("crone1@shakespeare.lit/desktop".toJID(), invitation.sender)

		muc.accept(invitation, "thirdwitch")
			.send()
		assertEquals(
			tigase.halcyon.core.xmpp.modules.muc.State.RequestSent,
			assertNotNull(muc.store.findRoom("darkcave@macbeth.shakespeare.lit".toBareJID())).state
		)
		assertContains(presence {
			to = "darkcave@macbeth.shakespeare.lit/thirdwitch".toJID()
			"x" {
				xmlns = "http://jabber.org/protocol/muc"
				"password" { +"cauldronburn" }
			}
		}, sentElements.removeLast(), "Invalid output stanza,")
	}

	@Test
	fun retrieve_update_config() {
		val events = mutableListOf<Event>()
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		halcyon.eventBus.register<Event> { events.add(it) }
		val room = createRoomAndJoin(muc)

		var config: JabberDataForm? = null

		val idGet = muc.retrieveRoomConfig(room)
			.response {
				it.onSuccess { config = it }
			}
			.send().id

		assertContains(iq {
			to = "coven@chat.shakespeare.lit".toJID()
			type = IQType.Get
			"query" { xmlns = "http://jabber.org/protocol/muc#owner" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		processReceived(iq {
			attributes["id"] = idGet
			from = "coven@chat.shakespeare.lit".toJID()
			type = IQType.Result
			"query" {
				xmlns = "http://jabber.org/protocol/muc#owner"
				"x" {
					xmlns = "jabber:x:data"
					attributes["type"] = "form"
					"title" { +"Configuration for \"coven\" Room" }
					"field" {
						attributes["type"] = "hidden"
						attributes["var"] = "FORM_TYPE"
						"value" { +"http://jabber.org/protocol/muc#roomconfig" }
					}
					"field" {
						attributes["type"] = "text-single"
						attributes["label"] = "Natural-Language Room Name"
						attributes["var"] = "muc#roomconfig_roomname"
						"value" { +"A Dark Cave" }
					}
					"field" {
						attributes["type"] = "boolean"
						attributes["label"] = "Make Room Persistent"
						attributes["var"] = "muc#roomconfig_persistentroom"
						"value" { +"0" }
					}
				}
			}
		})
		assertEquals(
			"http://jabber.org/protocol/muc#roomconfig", assertNotNull(config).getFieldByVar("FORM_TYPE")?.fieldValue
		)

		var updateOk = false
		val idSet = muc.updateRoomConfig(room, config!!)
			.response {
				it.onFailure { fail() }
				it.onSuccess { updateOk = true }
			}
			.send().id

		assertContains(iq {
			to = "coven@chat.shakespeare.lit".toJID()
			type = IQType.Set
			"query" {
				xmlns = "http://jabber.org/protocol/muc#owner"
				"x" {
					xmlns = "jabber:x:data"
					attributes["type"] = "submit"
					"field" {
						attributes["var"] = "FORM_TYPE"
						"value" { +"http://jabber.org/protocol/muc#roomconfig" }
					}
					"field" {
						attributes["var"] = "muc#roomconfig_roomname"
						"value" { +"A Dark Cave" }
					}
					"field" {
						attributes["var"] = "muc#roomconfig_persistentroom"
						"value" { +"0" }
					}
				}
			}
		}, sentElements.removeLast(), "Invalid output stanza,")

		processReceived(iq {
			attributes["id"] = idSet
			type = IQType.Result
			from = "coven@chat.shakespeare.lit".toJID()
		})

		assertTrue(updateOk)
	}

	@Test
	fun retrieveUpdateRoomAffiliations() {
		val events = mutableListOf<Event>()
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		halcyon.eventBus.register<Event> { events.add(it) }
		val room = createRoomAndJoin(muc)

		var result: Collection<RoomAffiliation>? = null
		val idGet = muc.retrieveAffiliations(room, filter = Affiliation.Member)
			.response {
				it.onSuccess {
					result = it
				}
			}
			.send().id
		assertContains(iq {
			to = "coven@chat.shakespeare.lit".toJID()
			type = IQType.Get
			"query" {
				xmlns = "http://jabber.org/protocol/muc#admin"
				"item" {
					attributes["affiliation"] = "member"
				}
			}
		}, sentElements.removeLast(), "Invalid output stanza,")

		processReceived(iq {
			attributes["id"] = idGet
			from = "coven@chat.shakespeare.lit".toJID()
			type = IQType.Result
			"query" {
				xmlns = "http://jabber.org/protocol/muc#admin"
				"item" {
					attributes["affiliation"] = "member"
					attributes["jid"] = "hag66@shakespeare.lit"
					attributes["nick"] = "thirdwitch"
					attributes["role"] = "participant"
				}
			}
		})

		assertNotNull(result).let {
			assertEquals(1, it.size)
			assertNotNull(it.first()).let {
				assertEquals(Affiliation.Member, it.affiliation)
				assertEquals(Role.Participant, it.role)
				assertEquals("thirdwitch", it.nickname)
				assertEquals("hag66@shakespeare.lit".toJID(), it.jid)
			}
		}

		var updated = false
		val idSet = muc.updateAffiliations(
			room,
			result!!.toMutableList()
				.apply {
					RoomAffiliation("hecate@shakespeare.lit".toJID(), Affiliation.Member, null, null)
				})
			.response { it.onSuccess { updated = true } }
			.send().id

		processReceived(iq {
			attributes["id"] = idSet
			from = "coven@chat.shakespeare.lit".toJID()
			type = IQType.Result
		})

		assertTrue(updated)
	}

	@Test
	fun setRoomSubject() {
		val events = mutableListOf<Event>()
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		halcyon.eventBus.register<Event> { events.add(it) }
		val room = createRoomAndJoin(muc)

		muc.updateRoomSubject(room, "test")
			.send()
		assertContains(message {
			to = "coven@chat.shakespeare.lit".toJID()
			type = MessageType.Groupchat
			"subject" { +"test" }
		}, sentElements.removeLast(), "Invalid output stanza,")
	}

	@Test
	fun selfPing() {
		val events = mutableListOf<Event>()
		val muc: MUCModule = halcyon.getModule(MUCModule.TYPE)
		halcyon.eventBus.register<Event> { events.add(it) }
		val room = createRoomAndJoin(muc)

		var pong: PingModule.Pong? = null
		val pingId = muc.ping(room)
			.response {
				it.onSuccess { pong = it }
			}
			.send().id
		assertContains(iq {
			to = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			type = IQType.Get
			"ping" { xmlns = "urn:xmpp:ping" }
		}, sentElements.removeLast(), "Invalid output stanza,")

		processReceived(iq {
			attributes["id"] = pingId
			from = "coven@chat.shakespeare.lit/thirdwitch".toJID()
			type = IQType.Result
		})

		assertNotNull(pong)

	}

}