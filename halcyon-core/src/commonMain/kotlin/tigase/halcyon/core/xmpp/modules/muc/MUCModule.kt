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
@file:Suppress("UnusedVariable", "UNUSED_VARIABLE", "UnusedParameter", "UNUSED_PARAMETER", "unused")

package tigase.halcyon.core.xmpp.modules.muc

import kotlinx.datetime.Instant
import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.timestampToISO8601
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.modules.PingModule
import tigase.halcyon.core.xmpp.modules.mix.isMixMessage
import tigase.halcyon.core.xmpp.stanzas.*

/**
 * Room joining state.
 */
enum class State {

    NotJoined, RequestSent, Joined
}

/**
 * Representation of MUC room occupant.
 */
class Occupant(pr: Presence) {

    /**
     * Last received presence stanza of occupant.
     */
    var presence: Presence = pr
        internal set

    /**
     * Role of room occupant.
     */
    val role: Role
        get() = MucUserExt.createUserExt(presence)?.role ?: Role.None

    /**
     * Affiliation of room occupant.
     */
    val affiliation: Affiliation
        get() = MucUserExt.createUserExt(presence)?.affiliation ?: Affiliation.None

}

/**
 * Representation of MUC room.
 * @param roomJID JabberID of room
 * @param nickname Nickname under which the library connects to the room.
 * @param state current state of connection with room
 * @param password room password (optional)
 * @param lastMessageTimestamp timestamp of last received message.
 *
 */
open class Room(
    val roomJID: BareJID,
    var nickname: String,
    var password: String?,
    var state: State = State.NotJoined,
    var lastMessageTimestamp: Instant? = null,
) {

    internal val occupants = mutableMapOf<String, Occupant>()

    /**
     * Returns all occupants of room.
     */
    fun occupants(): Map<String, Occupant> = occupants

}

/**
 * Interface to store rooms data.
 */
interface MUCStore {

    /**
     * Returns complete room object or `null` if room doesn't exist.
     * @param roomJID JabberID of room to find.
     */
    fun findRoom(roomJID: BareJID): Room?

    /**
     * Creates and stores room object.
     * @param JabberID of room.
     * @param nickname nickname under which library will join to room.
     */
    fun createRoom(roomJID: BareJID, nickname: String): Room
}

/**
 * Room invitation.
 * @param roomjid JabberID of MUC room.
 * @param sender sender of invitation.
 * @param password room password.
 * @param reason human-readable reason of sending invitation.
 * @param direct `true` if it is direct invitation.
 */
data class Invitation(
    val roomjid: BareJID, val sender: JID, val password: String?, val reason: String?, val direct: Boolean,
)

/**
 * Events related to MUC server.
 */
sealed class MucEvents : Event(TYPE) {

    /**
     * An invitation to MUC Room is received.
     * @param invitation received invitation
     */
    class InvitationReceived(val invitation: Invitation) : MucEvents()

    companion object : EventDefinition<MucEvents> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.muc.MucEvents"
    }
}

/**
 * Affiliation data of occupant.
 * @param jid JabberID of occupant (if present).
 * @param affiliation affiliation of occupant.
 * @param nickname nickname of occupant
 * @param role role of occupant.
 */
data class RoomAffiliation(val jid: JID?, val affiliation: Affiliation, val nickname: String?, val role: Role?)

/**
 * Events related to MUC Room.
 */
sealed class MucRoomEvents(val room: Room) : Event(TYPE) {

    // presence related events
    /**
     * MUC Server accepted join request.
     */
    class YouJoined(room: Room, val presence: Presence, val nickname: String) : MucRoomEvents(room)

    /**
     * Fired when you leaved room.
     */
    class YouLeaved(room: Room, val presence: Presence) : MucRoomEvents(room)

    /**
     * Server not accepted join request.
     */
    class JoinError(room: Room, val presence: Presence, val condition: ErrorCondition) : MucRoomEvents(room)

    /**
     * Event informs that room you joined is just created (by join request).
     */
    class Created(room: Room) : MucRoomEvents(room)

    /**
     * Informs that new occupant joined to room.
     */
    class OccupantCame(room: Room, val presence: Presence, val nickname: String) : MucRoomEvents(room)

    /**
     * Informs that occupant leaves the room.
     */
    class OccupantLeave(room: Room, val presence: Presence, val nickname: String) : MucRoomEvents(room)

    /**
     * Informs that occupant updated his presence.
     */
    class OccupantChangedPresence(room: Room, val presence: Presence, val nickname: String) : MucRoomEvents(room)

    // message related events

    /**
     * Event fired when group chat message from room is received.
     */
    class ReceivedMessage(room: Room, val nickname: String?, val message: Message) : MucRoomEvents(room)

    companion object : EventDefinition<MucRoomEvents> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.muc.MucRoomEvents"
    }
}

/**
 * Representation of
 * ```xml
 * <x xmlns='http://jabber.org/protocol/muc#user'/>
 * ```
 */
class MucUserExt(private val element: Element) {

    private val _statuses = mutableListOf<Int>()
    val statuses: List<Int> = _statuses

    val role: Role
        get() = element.getChildren("item").mapNotNull { it.attributes["role"] }.map { r ->
            Role.values().first { it.xmppValue == r }
        }.firstOrNull() ?: Role.None

    val affiliation: Affiliation
        get() = element.getChildren("item").mapNotNull { it.attributes["affiliation"] }.map { a ->
            Affiliation.values().first { it.xmppValue == a }
        }.firstOrNull() ?: Affiliation.None

    init {
        _statuses.addAll(extractStatuses())
    }

    private fun extractStatuses(): List<Int> {
        return element.getChildren("status").map { element -> element.attributes["code"]?.toInt() ?: 0 }.toList()
    }

    companion object {

        fun createUserExt(presence: Presence): MucUserExt? {
            val x = presence.getChildrenNS("x", "${MUCModule.XMLNS}#user") ?: return null
            return MucUserExt(x)
        }

    }

}

/**
 * Configuration of [MUCModule].
 */
@HalcyonConfigDsl
interface MUCModuleConfig {
    /**
     * Specify a store to keep connected rooms.
     */
    var store: MUCStore
}

/**
 * Module is implementing Multi-User Chat ([XEP-0045](https://xmpp.org/extensions/xep-0045.html)).
 *
 */
class MUCModule(override val context: Context) : XmppModule, MUCModuleConfig {

    /**
     * Module is implementing Multi-User Chat ([XEP-0045](https://xmpp.org/extensions/xep-0045.html)).
     *
     */
    companion object : XmppModuleProvider<MUCModule, MUCModuleConfig> {

        const val XMLNS = "http://jabber.org/protocol/muc"
        override val TYPE = XMLNS
        override fun instance(context: Context): MUCModule = MUCModule(context)

        override fun configure(module: MUCModule, cfg: MUCModuleConfig.() -> Unit) = module.cfg()

    }

//	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.muc.MUCModule")

    override val type = TYPE
    override val criteria: Criteria = Criterion.element { element -> calculateAction(element) != Action.Skip }
    override val features: Array<String>? = null

    private enum class Action {

        Skip, MediatedInvitationDecline, MediatedInvitation, DirectInvitation, Message, Presence
    }

    override var store: MUCStore = DefaultMUCStore()
    private fun isFromKnownMUCRoom(jid: BareJID): Boolean {
        return store.findRoom(jid) != null
    }

    private fun calculateAction(element: Element, checkRoomStore: Boolean = true): Action {
        if (element.isMixMessage()) return Action.Skip
        val from: BareJID = element.attributes["from"]?.toBareJID() ?: return Action.Skip
        if (element.name == Message.NAME) {
            val type = element.attributes["type"]
            if (type == MessageType.Error.value) {
                return if (!checkRoomStore || isFromKnownMUCRoom(from)) Action.Message else Action.Skip
            } else if (type == MessageType.Groupchat.value) {
                return Action.Message
            }

            for (x in element.getChildren("x")) {
                if (x.xmlns == "jabber:x:conference") return Action.DirectInvitation
                if (x.xmlns == "$XMLNS#user" && x.getFirstChild("invite") != null) return Action.MediatedInvitation
                if (x.xmlns == "$XMLNS#user" && x.getFirstChild("decline") != null) return Action.MediatedInvitationDecline
            }
        }
        if (element.name == Presence.NAME) {
            if (!checkRoomStore) {
                return Action.Presence
            } else if (isFromKnownMUCRoom(from)) {
                return Action.Presence
            }
        }

        return Action.Skip
    }

    override fun process(element: Element) {
        when (calculateAction(element, false)) {
            Action.Message -> processMessage(wrap(element))
            Action.Presence -> processPresence(wrap(element))
            Action.MediatedInvitationDecline -> processInvitationDeclinedMessage(wrap(element))
            Action.MediatedInvitation -> processMediatedInvitationMessage(wrap(element))
            Action.DirectInvitation -> processDirectInvitationMessage(wrap(element))
            Action.Skip -> throw XMPPException(ErrorCondition.FeatureNotImplemented)
        }
    }

    private fun processMessage(stanza: Message) {
        if (stanza.from?.bareJID == context.boundJID?.bareJID) {
            // this may be a message carbons (sent), then just ignore as we want to process just messages from MUC room
            return;
        }
        val room = store.findRoom(stanza.from!!.bareJID) ?: throw XMPPException(ErrorCondition.ServiceUnavailable)
        val nickname = stanza.from?.resource ?: return

        if (stanza.type != MessageType.Error) {
            context.eventBus.fire(MucRoomEvents.ReceivedMessage(room, nickname, stanza))
        }
    }

    private fun processPresence(stanza: Presence) {
        val room = store.findRoom(stanza.from!!.bareJID) ?: throw XMPPException(ErrorCondition.ServiceUnavailable)
        val nickname = stanza.from!!.resource

        if (stanza.type == PresenceType.Error && room.state != State.Joined && nickname == null) {
            room.state = State.NotJoined
            context.eventBus.fire(
                MucRoomEvents.JoinError(
                    room, stanza, stanza.getErrorConditionOrNull() ?: ErrorCondition.UndefinedCondition
                )
            )
        }

        if (nickname == null) return

        val mucExt = MucUserExt.createUserExt(stanza)

        val (occupant, presenceOld) = if (room.occupants.containsKey(nickname)) {
            val p = room.occupants[nickname]!!
            Pair(p, p.presence)
        } else {
            Pair(Occupant(stanza), null)
        }

        // TODO kicked out from room
        // TODO occupant nickname change

        val selfPresence = (mucExt != null && mucExt.statuses.contains(110)) || nickname == room.nickname

        if (room.state == State.Joined && selfPresence) {
            // you leave room
            room.state = State.NotJoined
            room.occupants.clear()
            context.eventBus.fire(MucRoomEvents.YouLeaved(room, stanza))
        } else if (room.state != State.Joined && selfPresence) {
            // own presence
            room.state = State.Joined
            room.occupants[nickname] = occupant
            context.eventBus.fire(MucRoomEvents.YouJoined(room, stanza, nickname))
        } else if ((presenceOld == null || presenceOld.type == PresenceType.Unavailable) && stanza.type == null) {
            // other occupant came
            room.occupants[nickname] = occupant
            context.eventBus.fire(MucRoomEvents.OccupantCame(room, stanza, nickname))
        } else if (stanza.type == PresenceType.Unavailable) {
            // other occupant leaves
            room.occupants.remove(nickname)
            context.eventBus.fire(MucRoomEvents.OccupantLeave(room, stanza, nickname))
        } else {
            // presence update
            occupant.presence = stanza
            context.eventBus.fire(MucRoomEvents.OccupantChangedPresence(room, stanza, nickname))
        }

        if (mucExt != null && mucExt.statuses.contains(201)) context.eventBus.fire(MucRoomEvents.Created(room))
    }

    private fun processInvitationDeclinedMessage(stanza: Message) {
        TODO("Not yet implemented")
    }

    private fun processMediatedInvitationMessage(stanza: Message) {
        val roomJid = stanza.from?.bareJID ?: throw XMPPException(ErrorCondition.BadRequest)
        val invite = stanza.getChildrenNS("x", "$XMLNS#user")?.getFirstChild("invite") ?: throw XMPPException(
            ErrorCondition.BadRequest
        )
        val sender = invite.attributes["from"]?.toJID() ?: throw XMPPException(ErrorCondition.BadRequest)
        val reason = invite.getFirstChild("reason")?.value
        val password = stanza.getChildrenNS("x", "$XMLNS#user")?.getFirstChild("password")?.value
        context.eventBus.fire(MucEvents.InvitationReceived(Invitation(roomJid, sender, password, reason, false)))
    }

    private fun processDirectInvitationMessage(stanza: Message) {
        val invite = stanza.getChildrenNS("x", "jabber:x:conference") ?: throw XMPPException(ErrorCondition.BadRequest)
        val sender = stanza.from ?: throw XMPPException(ErrorCondition.BadRequest)
        val roomJid = invite.attributes["jid"]?.toBareJID() ?: throw XMPPException(ErrorCondition.BadRequest)
        val password = invite.attributes["password"]
        val reason = invite.attributes["reason"]
        context.eventBus.fire(MucEvents.InvitationReceived(Invitation(roomJid, sender, password, reason, true)))
    }

    /**
     * Builds join request to MUC Room.
     * @parami
     */
    fun join(roomJID: BareJID, nickname: String, password: String? = null): RequestBuilder<Unit, Presence> {
        val room = store.findRoom(roomJID) ?: store.createRoom(roomJID, nickname)
        room.password = password
        return context.request.presence {
            to = room.roomJID.copy(resource = nickname)
            "x" {
                xmlns = XMLNS
                room.password?.let { pwd ->
                    "password" { +pwd }
                }
            }
            room.lastMessageTimestamp?.let { lmt ->
                "history" {
                    attributes["since"] = timestampToISO8601(lmt)
                }
            }
        }.onSend {
            room.state = State.RequestSent
        }
    }

    /**
     * Builds request to leaves MUC Room.
     */
    fun leave(room: Room): RequestBuilder<Unit, Presence> {
        return context.request.presence {
            to = room.roomJID.copy(resource = room.nickname)
            type = PresenceType.Unavailable
        }
    }

    /**
     * Builds room destroy request.
     */
    fun destroy(room: Room): RequestBuilder<Unit, IQ> = context.request.iq {
        type = IQType.Set
        to = room.roomJID
        "query" {
            xmlns = "$XMLNS#owner"
            "destroy" {}
        }
    }.map { }

    /**
     * Builds mediated invitation request.
     */
    fun invite(room: Room, invitedJid: BareJID, reason: String? = null): RequestBuilder<Unit, Message> =
        context.request.message {
            to = room.roomJID
            "x" {
                xmlns = "$XMLNS#user"
                "invite" {
                    attributes["to"] = invitedJid.toString()
                    if (reason != null) "reason" { +reason }
                }
            }
        }

    /**
     * Builds direct invitation request.
     */
    fun inviteDirectly(room: Room, invitedJid: BareJID, reason: String? = null): RequestBuilder<Unit, Message> =
        context.request.message {
            to = invitedJid
            "x" {
                xmlns = "jabber:x:conference"
                attributes["jid"] = room.roomJID.toString()
                reason?.let {
                    attributes["reason"] = reason
                }
                room.password?.let {
                    attributes["password"] = it
                }
            }
        }

    /**
     * Builds retrieve room configuration request. In response it returns data form with configuration.
     */
    fun retrieveRoomConfig(room: Room): RequestBuilder<JabberDataForm, IQ> = context.request.iq {
        to = room.roomJID
        type = IQType.Get
        "query" {
            xmlns = "$XMLNS#owner"
        }
    }.map { iq ->
        val x = iq.getChildrenNS("query", "$XMLNS#owner")?.getFirstChild("x")
            ?: throw XMPPException(ErrorCondition.BadRequest, "Missing data form.")
        JabberDataForm(x)
    }

    /**
     * Builds update room configuration request.
     */
    fun updateRoomConfig(room: Room, form: JabberDataForm): RequestBuilder<Unit, IQ> = context.request.iq {
        to = room.roomJID
        type = IQType.Set
        "query" {
            xmlns = "$XMLNS#owner"
            addChild(form.createSubmitForm())
        }
    }.map { }

    /**
     * Builds group chat message request.
     */
    fun message(room: Room, messageBody: String): RequestBuilder<Unit, Message> {
        return context.request.message {
            to = room.roomJID
            type = MessageType.Groupchat
            body = messageBody
        }
    }

    /**
     * Builds decline request for received invitation.
     */
    fun decline(invitation: Invitation, reason: String? = null): RequestBuilder<Unit, Message> {
        if (invitation.direct) throw HalcyonException("Direct invitation should be silently ignored.")
        return context.request.message {
            to = invitation.roomjid
            "x" {
                xmlns = "$XMLNS#user"
                "decline" {
                    attributes["to"] = invitation.sender.bareJID.toString()
                    if (reason != null) "reason" { +reason }
                }
            }
        }
    }

    /**
     * Builds join request to MUC Room based on received invitation.
     */
    fun accept(invitation: Invitation, nickname: String): RequestBuilder<Unit, Presence> {
        return join(invitation.roomjid, nickname, invitation.password)
    }

    /**
     * Builds request for retrieve affiliations list from MUC room. In response it returns collection of RoomAffiliation.
     */
    fun retrieveAffiliations(room: Room, filter: Affiliation? = null): RequestBuilder<Collection<RoomAffiliation>, IQ> =
        context.request.iq {
            to = room.roomJID
            type = IQType.Get
            "query" {
                xmlns = "$XMLNS#admin"
                filter?.let {
                    "item" {
                        attributes["affiliation"] = filter.xmppValue
                    }
                }
            }
        }.map { iq ->
            val q = iq.getChildrenNS("query", "$XMLNS#admin") ?: throw XMPPException(ErrorCondition.BadRequest)
            q.getChildren("item").map {
                val aff = it.attributes["affiliation"]?.let { aff ->
                    Affiliation.values().first { it.xmppValue == aff }
                } ?: Affiliation.None
                val jid = it.attributes["jid"]?.toJID()
                val nickname = it.attributes["nick"]
                val role = it.attributes["role"]?.let { rl ->
                    Role.values().first { it.xmppValue == rl }
                }
                RoomAffiliation(jid, aff, nickname, role)
            }
        }

    /**
     * Builds request for update affiliations list.
     */
    fun updateAffiliations(room: Room, affiliations: Collection<RoomAffiliation>): RequestBuilder<Unit, IQ> =
        context.request.iq {
            to = room.roomJID
            type = IQType.Set
            "query" {
                xmlns = "$XMLNS#admin"
                affiliations.forEach { a ->
                    "item" {
                        attributes["affiliation"] = a.affiliation.xmppValue
                        a.jid?.let { attributes["jid"] = it.toString() }
                        a.role?.let { attributes["role"] = it.xmppValue }
                    }
                }
            }
        }.map { }

    /**
     * Builds request for set room subject.
     */
    fun updateRoomSubject(room: Room, subject: String?): RequestBuilder<Unit, Message> = context.request.message {
        to = room.roomJID
        type = MessageType.Groupchat
        "subject" {
            if (subject != null) {
                +subject
            }
        }
    }

    /**
     * Builds request from self ping.
     */
    fun ping(room: Room): RequestBuilder<PingModule.Pong, IQ> =
        context.modules.get<PingModule>(PingModule.TYPE).ping(FullJID(room.roomJID, room.nickname))

}