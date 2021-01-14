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
package tigase.halcyon.core.xmpp.modules.mix

import kotlinx.serialization.Serializable
import tigase.halcyon.core.Context
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.requests.RequestConsumerBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xml.getChildContent
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.RSM
import tigase.halcyon.core.xmpp.modules.mam.ForwardedStanza
import tigase.halcyon.core.xmpp.modules.mam.MAMModule
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.modules.roster.RosterItemAnnotation
import tigase.halcyon.core.xmpp.modules.roster.RosterItemAnnotationProcessor
import tigase.halcyon.core.xmpp.modules.roster.RosterModule
import tigase.halcyon.core.xmpp.stanzas.*
import tigase.halcyon.core.xmpp.toBareJID
import tigase.halcyon.core.xmpp.toJID

@Serializable
data class MIXRosterItemAnnotation(val participantId: String) : RosterItemAnnotation

@Serializable
data class MIXInvitation(val inviter: BareJID, val invitee: BareJID, val channel: BareJID, val token: String?)

data class MIXMessageEvent(val channel: BareJID, val stanza: Message, val timestamp: Long) : Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.mix.MIXMessageEvent"
	}
}

data class JoinResponse(val jid: JID, val nick: String, val nodes: Array<String>)

data class CreateResponse(val jid: BareJID, val name: String)

data class Participant(val id: String, val nick: String?, val jid: BareJID?)

class MIXModule(override val context: Context) : XmppModule, RosterItemAnnotationProcessor {

	companion object {

		const val XMLNS = "urn:xmpp:mix:core:1"
		const val MISC_XMLNS = "urn:xmpp:mix:misc:0"
		const val TYPE = XMLNS

		const val NODE_ALLOWED = "urn:xmpp:mix:nodes:allowed"
		const val NODE_BANNED = "urn:xmpp:mix:nodes:banned"
		const val NODE_PARTICIPANTS = "urn:xmpp:mix:nodes:participants"
		const val NODE_PRESENCE = "urn:xmpp:mix:nodes:presence"
		const val NODE_MESSAGES = "urn:xmpp:mix:nodes:messages"
	}

	override val criteria: Criteria? = Criterion.element(this@MIXModule::checkCriteria)
	override val type = TYPE
	override val features = arrayOf(XMLNS)

	private lateinit var rosterModule: RosterModule
	private lateinit var pubsubModule: PubSubModule
	private lateinit var mamModule: MAMModule

	override fun initialize() {
		rosterModule = context.modules.getModule(RosterModule.TYPE)
		pubsubModule = context.modules.getModule(PubSubModule.TYPE)
		mamModule = context.modules.getModule(MAMModule.TYPE)
	}

	private fun checkCriteria(message: Element): Boolean {
		if (message.name != Message.NAME) return false
		if (message.getChildrenNS("mix", XMLNS) == null) return false
		val fromJid = message.attributes["from"]?.toBareJID() ?: return false
		val item = rosterModule.store.getItem(fromJid) ?: return false
		return item.annotations.any { it is MIXRosterItemAnnotation }
	}

	override fun process(element: Element) {
		process(wrap(element), currentTimestamp())
	}

	private fun process(message: Message, time: Long) {
		context.eventBus.fire(MIXMessageEvent(message.from!!.bareJID, message, time))
	}

	private fun myJID(): JID =
		context.modules.getModule<BindModule>(BindModule.TYPE).boundJID ?: throw HalcyonException("Resource not bound.")

	private fun invitationToElement(invitation: MIXInvitation, withXmlns: Boolean = false): Element {
		return element("invitation") {
			if (withXmlns) xmlns = MISC_XMLNS
			"inviter"{ +invitation.inviter.toString() }
			"invitee"{ +invitation.invitee.toString() }
			"channel"{ +invitation.channel.toString() }
			invitation.token?.let {
				"token"{ +it }
			}
		}
	}

	fun create(mixComponent: BareJID, name: String): RequestBuilder<CreateResponse, IQ> {
		return context.request.iq {
			type = IQType.Set
			to = mixComponent.toJID()
			"create"{
				xmlns = XMLNS
				attributes["channel"] = name
			}
		}.map {
			val cr = it.getChildrenNS("create", XMLNS)!!
			val chname = cr.attributes["channel"]!!
			CreateResponse(BareJID(chname, mixComponent.domain), chname)
		}
	}

	fun createAllowed(channel: BareJID): RequestBuilder<Unit, IQ> {
		return pubsubModule.create(channel.toJID(), NODE_ALLOWED)
	}

	fun createBanned(channel: BareJID): RequestBuilder<Unit, IQ> {
		return pubsubModule.create(channel.toJID(), NODE_BANNED)
	}

	fun addToAllowed(channel: BareJID, participant: BareJID): RequestBuilder<Unit, IQ> {
		return pubsubModule.publish(channel.toJID(), NODE_ALLOWED, participant.toString()).map { Unit }
	}

	fun addToBanned(channel: BareJID, participant: BareJID): RequestBuilder<Unit, IQ> {
		return pubsubModule.publish(channel.toJID(), NODE_BANNED, participant.toString()).map { Unit }
	}

	fun createInvitation(
		invitee: BareJID, channel: BareJID, inviter: BareJID = myJID().bareJID, token: String? = null
	): MIXInvitation {
		return MIXInvitation(inviter, invitee, channel, token)
	}

	fun invitationMessage(invitation: MIXInvitation, message: String): RequestBuilder<Unit, Message> {
		return context.request.message {
			to = invitation.invitee.toJID()
			body = message
			addChild(invitationToElement(invitation, true))
		}
	}

	fun join(invitation: MIXInvitation, nick: String): RequestBuilder<JoinResponse, IQ> =
		join(invitation.channel, nick, invitation)

	fun join(
		channel: BareJID, nick: String, invitation: MIXInvitation? = null
	): RequestBuilder<JoinResponse, IQ> {
		return context.request.iq {
			type = IQType.Set
			to = myJID().bareJID.toJID()
			"client-join"{
				xmlns = "urn:xmpp:mix:pam:2"
				attributes["channel"] = channel.toString()
				"join"{
					xmlns = XMLNS
					"nick"{
						+nick
					}
					"subscribe"{ attributes["node"] = NODE_MESSAGES }
					"subscribe"{ attributes["node"] = NODE_PRESENCE }
					"subscribe"{ attributes["node"] = NODE_PARTICIPANTS }
					"subscribe"{ attributes["node"] = "urn:xmpp:mix:nodes:info" }
					invitation?.let {
						addChild(invitationToElement(it))
					}
				}
			}
		}.map { iq ->
			val join = iq.getChildrenNS("client-join", "urn:xmpp:mix:pam:2")!!.getChildrenNS("join", XMLNS)!!
			val nodes = join.getChildren("subscribe").map { it.attributes["node"]!! }.toTypedArray()
			val nck = join.getChildren("subscribe").firstOrNull()?.value ?: nick
			JoinResponse(join.attributes["jid"]!!.toJID(), nck, nodes)
		}
	}

	fun leave(channel: BareJID): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			type = IQType.Set
			"client-leave"{
				xmlns = "urn:xmpp:mix:pam:2"
				attributes["channel"] = channel.toString()
				"leave"{
					xmlns = XMLNS
				}
			}
		}.map { Unit }
	}

	private fun createParticipant(id: String, p: Element): Participant? {
		return Participant(id, p.getChildContent("nick"), p.getChildContent("jid")?.toBareJID())
	}

	fun retrieveParticipants(channel: BareJID): RequestBuilder<Collection<Participant>, IQ> {
		return pubsubModule.retrieveItem(channel.toJID(), NODE_PARTICIPANTS).map { r ->
			r.items.mapNotNull { item -> createParticipant(item.id, item.content!!) }
		}
	}

	fun message(channel: BareJID, message: String): RequestBuilder<Unit, Message> {
		return context.request.message {
			to = channel.toJID()
			type = MessageType.Groupchat
			body = message
		}
	}

	override fun prepareRosterGetRequest(stanza: IQ) {
		stanza.getChildrenNS("query", RosterModule.XMLNS)?.add(element("annotate") {
			xmlns = "urn:xmpp:mix:roster:0"
		})
	}

	override fun processRosterItem(item: Element): RosterItemAnnotation? {
		return item.getChildrenNS("channel", "urn:xmpp:mix:roster:0")?.let { channel ->
			MIXRosterItemAnnotation(channel.attributes["participant-id"]!!)
		}
	}

	fun retrieveHistory(
		fromChannel: BareJID? = null, with: String? = null, rsm: RSM? = null, start: Long? = null, end: Long? = null
	): RequestConsumerBuilder<ForwardedStanza<Message>, MAMModule.Fin, IQ> {
		val node = if (fromChannel == null) null else NODE_MESSAGES
		return mamModule.query(fromChannel, node, rsm, with, start, end)
	}

}

data class MixAnnotation(val nick: String, val jid: BareJID?)

fun Element.isMixMessage(): Boolean {
	if (this.name != Message.NAME) return false
	return this.getChildrenNS("mix", MIXModule.XMLNS) != null
}

fun Element.getMixAnnotation(): MixAnnotation? {
	if (this.name != Message.NAME) return null
	return this.getChildrenNS("mix", MIXModule.XMLNS)?.let {
		val nick = it.getFirstChild("nick")!!.value!!
		val jid = it.getFirstChild("jid")?.value?.toBareJID()
		MixAnnotation(nick, jid)
	}
}

fun Element.getMixInvitation(): MIXInvitation? = this.getChildrenNS("invitation", MIXModule.MISC_XMLNS)?.let {
	val inviter = it.getChildContent("inviter") ?: return null
	val invitee = it.getChildContent("invitee") ?: return null
	val channel = it.getChildContent("channel") ?: return null
	val token = it.getChildContent("token")

	MIXInvitation(inviter.toBareJID(), invitee.toBareJID(), channel.toBareJID(), token)
}


