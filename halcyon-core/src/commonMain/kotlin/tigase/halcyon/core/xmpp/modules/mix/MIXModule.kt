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

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
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
import tigase.halcyon.core.requests.RequestConsumerBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xml.getChildContent
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.forms.FieldType
import tigase.halcyon.core.xmpp.forms.FormType
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.modules.RSM
import tigase.halcyon.core.xmpp.modules.avatar.UserAvatarModule
import tigase.halcyon.core.xmpp.modules.mam.ForwardedStanza
import tigase.halcyon.core.xmpp.modules.mam.MAMModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.modules.pubsub.Affiliation
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubItemEvent
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.modules.roster.RosterItemAnnotation
import tigase.halcyon.core.xmpp.modules.roster.RosterItemAnnotationProcessor
import tigase.halcyon.core.xmpp.modules.roster.RosterModule
import tigase.halcyon.core.xmpp.stanzas.*

@Serializable
data class MIXRosterItemAnnotation(val participantId: String) : RosterItemAnnotation

@Serializable
data class MIXInvitation(val inviter: BareJID, val invitee: BareJID, val channel: BareJID, val token: String?)

data class MIXMessageEvent(val channel: BareJID, val stanza: Message, val timestamp: Instant) : Event(TYPE) {

	companion object : EventDefinition<MIXMessageEvent> {

		override val TYPE = "tigase.halcyon.core.xmpp.modules.mix.MIXMessageEvent"
	}
}

data class JoinResponse(val jid: JID, val nick: String, val nodes: Array<String>, val participantId: String?) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is JoinResponse) return false

		if (jid != other.jid) return false
		if (nick != other.nick) return false
		return nodes.contentEquals(other.nodes)
	}

	override fun hashCode(): Int {
		var result = jid.hashCode()
		result = 31 * result + nick.hashCode()
		result = 31 * result + nodes.contentHashCode()
		return result
	}
}

data class ChannelInfo(val name: String?, val description: String?, val contacts: List<JID>?) {

	companion object {

		fun fromJabberDataForm(form: JabberDataForm): ChannelInfo? {
			if (form.getFieldByVar("FORM_TYPE")?.fieldValue != MIXModule.XMLNS) {
				return null;
			}
			val name = form.getFieldByVar("Name")?.fieldValue;
			val description = form.getFieldByVar("Description")?.fieldValue;
			val contacts = form.getFieldByVar("Contacts")?.fieldValues?.map { it.toJID() };

			return ChannelInfo(name, description, contacts)
		}

		fun fromElement(element: Element): ChannelInfo? {
			try {
				val form = JabberDataForm(element);
				return fromJabberDataForm(form);
			} catch (e: Exception) {
				return null;
			}
		}
	}

	fun toJabberDataForm(): JabberDataForm {
		val form = JabberDataForm.create(FormType.Form);
		form.addField("FORM_TYPE", FieldType.Hidden).fieldValue = MIXModule.XMLNS
		form.addField("Name", FieldType.TextSingle).fieldValue = name
		form.addField("Description", FieldType.TextSingle).fieldValue = description
		form.addField("Contact", FieldType.JidMulti).fieldValues = contacts?.map { it.toString() } ?: emptyList()
		return form;
	}

	fun toElement(): Element = toJabberDataForm().createSubmitForm();
	
}

data class CreateResponse(val jid: BareJID, val name: String)

data class Participant(val id: String, val nick: String?, val jid: BareJID?)

sealed class MixParticipantEvent(val channel: BareJID, val id: String): Event(TYPE) {
	class Joined(channel: BareJID, id: String, val jid: BareJID?, val nick: String?): MixParticipantEvent(channel, id) {
		fun participant(): Participant = Participant(id, nick, jid)
		override fun toString(): String {
			return "Joined(jid=$jid, nick=$nick)"
		}
	}
	class Left(channel: BareJID,id: String): MixParticipantEvent(channel, id) {}

	companion object : EventDefinition<MixParticipantEvent> {

		override val TYPE = "tigase.halcyon.core.xmpp.modules.mix.MixParticipantEventReceived"
	}

	override fun toString(): String {
		return "MixParticipantEvent(channel=$channel, id='$id')"
	}
}

data class MIXChannelInfoUpdatedEvent(val channel: BareJID, val info: ChannelInfo?) : Event(TYPE) {

    companion object : EventDefinition<MIXChannelInfoUpdatedEvent> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.mix.MIXChannelInfoUpdatedEvent"
    }
}

@HalcyonConfigDsl
interface MIXModuleConfig

enum class MixPermission {
	ChangeInfo,
	ChangeConfig,
	ChangeAvatar
}

class MIXModule(
	override val context: Context,
	private val rosterModule: RosterModule,
	private val pubsubModule: PubSubModule,
	private val mamModule: MAMModule,
) : XmppModule, RosterItemAnnotationProcessor, MIXModuleConfig {

	companion object : XmppModuleProvider<MIXModule, MIXModuleConfig> {

		const val XMLNS = "urn:xmpp:mix:core:1"
		const val MISC_XMLNS = "urn:xmpp:mix:misc:0"
		override val TYPE = XMLNS

		const val NODE_ALLOWED = "urn:xmpp:mix:nodes:allowed"
		const val NODE_BANNED = "urn:xmpp:mix:nodes:banned"
		const val NODE_PARTICIPANTS = "urn:xmpp:mix:nodes:participants"
		const val NODE_PRESENCE = "urn:xmpp:mix:nodes:presence"
		const val NODE_MESSAGES = "urn:xmpp:mix:nodes:messages"

		override fun instance(context: Context): MIXModule = MIXModule(
			context,
			rosterModule = context.modules.getModule(RosterModule.TYPE),
			pubsubModule = context.modules.getModule(PubSubModule.TYPE),
			mamModule = context.modules.getModule(MAMModule.TYPE)
		)

		override fun configure(module: MIXModule, cfg: MIXModuleConfig.() -> Unit) = module.cfg()

		override fun requiredModules() = listOf(RosterModule, PubSubModule, MAMModule)
	}

	override val criteria: Criteria = Criterion.element(this@MIXModule::checkCriteria)
	override val type = TYPE
	override val features = arrayOf(XMLNS)

	init {
		context.eventBus.register(PubSubItemEvent.TYPE) { event: PubSubItemEvent ->
			processPubSubEvent(event);
		}
	}

	private fun processPubSubEvent(event: PubSubItemEvent) {
		when (event.nodeName) {
			NODE_PARTICIPANTS -> processParticipantsEvent(event);
            "urn:xmpp:mix:nodes:info" -> {
                val channelJid = event.pubSubJID?.bareJID ?: return;
                val info = when (event) {
                    is PubSubItemEvent.Published -> event.content?.let { ChannelInfo.fromElement(it) }
                    is PubSubItemEvent.Retracted -> null
                }
                context.eventBus.fire(MIXChannelInfoUpdatedEvent(channelJid, info = info))
            }
			else -> {}
		}
	}

	private fun processParticipantsEvent(event: PubSubItemEvent) {
		val channel = event.pubSubJID?.bareJID ?: return;
		when (event) {
			is PubSubItemEvent.Published -> {
				val participant = event.itemId?.let { itemId -> event.content?.let { content -> createParticipant(itemId, content) } } ?: return;
				context.eventBus.fire(MixParticipantEvent.Joined(channel = channel, jid = participant.jid, id = participant.id, nick = participant.nick))
			}
			is PubSubItemEvent.Retracted -> {
				val id = event.itemId ?: return;
				context.eventBus.fire(MixParticipantEvent.Left(channel = channel, id = id))
			}
		}
	}

	private fun checkCriteria(message: Element): Boolean {
		if (message.name != Message.NAME) return false
		if (message.getChildrenNS("mix", XMLNS) == null) return false
		val fromJid = message.attributes["from"]?.toBareJID() ?: return false
		val item = rosterModule.store.getItem(fromJid) ?: return false
		return item.annotations.any { it is MIXRosterItemAnnotation }
	}

	override fun process(element: Element) {
		process(wrap(element), Clock.System.now())
	}

	private fun process(message: Message, time: Instant) {
		context.eventBus.fire(MIXMessageEvent(message.from!!.bareJID, message, time))
	}

	private fun myJID(): FullJID = context.boundJID ?: throw HalcyonException("Resource not bound.")

	private fun invitationToElement(invitation: MIXInvitation, withXmlns: Boolean = false): Element {
		return element("invitation") {
			if (withXmlns) xmlns = MISC_XMLNS
			"inviter" { +invitation.inviter.toString() }
			"invitee" { +invitation.invitee.toString() }
			"channel" { +invitation.channel.toString() }
			invitation.token?.let {
				"token" { +it }
			}
		}
	}

	fun create(mixComponent: BareJID, name: String?): RequestBuilder<CreateResponse, IQ> {
		return context.request.iq {
			type = IQType.Set
			to = mixComponent
			"create" {
				xmlns = XMLNS
				name?.let {
					attributes["channel"] = it
				}
			}
		}.map {
			val cr = it.getChildrenNS("create", XMLNS)!!
			val chname = cr.attributes["channel"]!!
			CreateResponse(BareJID(chname, mixComponent.domain), chname)
		}
	}

	fun createAllowed(channel: BareJID): RequestBuilder<Unit, IQ> {
		return pubsubModule.create(channel, NODE_ALLOWED)
	}

	fun createBanned(channel: BareJID): RequestBuilder<Unit, IQ> {
		return pubsubModule.create(channel, NODE_BANNED)
	}

	fun addToAllowed(channel: BareJID, participant: BareJID): RequestBuilder<Unit, IQ> {
		return pubsubModule.publish(channel, NODE_ALLOWED, participant.toString()).map { }
	}

	fun removeFromAllowed(channel: BareJID, participant: BareJID): RequestBuilder<Unit, IQ> {
		return pubsubModule.deleteItem(channel, NODE_ALLOWED, participant.toString()).map {}
	}

	fun addToBanned(channel: BareJID, participant: BareJID): RequestBuilder<Unit, IQ> {
		return pubsubModule.publish(channel, NODE_BANNED, participant.toString()).map { }
	}

	fun removeFromBanned(channel: BareJID, participant: BareJID): RequestBuilder<Unit, IQ> {
		return pubsubModule.deleteItem(channel, NODE_BANNED, participant.toString()).map {}
	}

	fun createInvitation(
		invitee: BareJID, channel: BareJID, inviter: BareJID = myJID().bareJID, token: String? = null,
	): MIXInvitation {
		return MIXInvitation(inviter, invitee, channel, token)
	}

	fun invitationMessage(invitation: MIXInvitation, message: String): RequestBuilder<Unit, Message> {
		return context.request.message {
			to = invitation.invitee
			body = message
			addChild(invitationToElement(invitation, true))
		}
	}

	fun retrieveAffiliations(channel: BareJID): RequestBuilder<Set<MixPermission>, IQ>{
		return pubsubModule.retrieveAffiliations(channel).map {
			it.mapNotNull {
				if (it.affiliation == Affiliation.Owner || it.affiliation == Affiliation.Publisher) {
					when (it.node) {
						"urn:xmpp:mix:nodes:info" -> MixPermission.ChangeInfo
						"urn:xmpp:mix:nodes:config" -> MixPermission.ChangeConfig
						"urn:xmpp:avatar:metadata" -> MixPermission.ChangeAvatar
						else -> null
					}
				} else {
					null
				}
			}.toSet()
		}
	}

	fun join(invitation: MIXInvitation, nick: String): RequestBuilder<JoinResponse, IQ> =
		join(invitation.channel, nick, invitation)

	fun join(
		channel: BareJID, nick: String, invitation: MIXInvitation? = null, presenceSubscription: Boolean = true
	): RequestBuilder<JoinResponse, IQ> {
		return context.request.iq {
			type = IQType.Set
			to = myJID().bareJID
			"client-join" {
				xmlns = "urn:xmpp:mix:pam:2"
				attributes["channel"] = channel.toString()
				"join" {
					xmlns = XMLNS
					"nick" {
						+nick
					}
					"subscribe" { attributes["node"] = NODE_MESSAGES }
					"subscribe" { attributes["node"] = NODE_PRESENCE }
					"subscribe" { attributes["node"] = NODE_PARTICIPANTS }
					"subscribe" { attributes["node"] = "urn:xmpp:mix:nodes:info" }
                    "subscribe" { attributes["node"] = UserAvatarModule.XMLNS_METADATA }
					invitation?.let {
						addChild(invitationToElement(it))
					}
				}
			}
		}.map { iq ->
			val join = iq.getChildrenNS("client-join", "urn:xmpp:mix:pam:2")!!.getChildrenNS("join", XMLNS)!!
			val nodes = join.getChildren("subscribe").map { it.attributes["node"]!! }.toTypedArray()
			val nck = join.getChildren("subscribe").firstOrNull()?.value ?: nick
			var jid = join.attributes["jid"]!!;
			val parts = jid.split('#');
			var id: String? = null;
			if (parts.size > 1) {
				id = parts[0];
				jid = parts[1];
			}
            context.modules.getModuleOrNull(PresenceModule)?.let { presenceModule ->
                presenceModule.sendSubscriptionSet(jid.toJID(), PresenceType.Subscribed).send()
                presenceModule.sendSubscriptionSet(jid.toJID(), PresenceType.Subscribe).send()
            }
			JoinResponse(jid.toJID(), nck, nodes, id)
		}
	}

	fun leave(channel: BareJID): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			type = IQType.Set
			"client-leave" {
				xmlns = "urn:xmpp:mix:pam:2"
				attributes["channel"] = channel.toString()
				"leave" {
					xmlns = XMLNS
				}
			}
		}.map { }
	}

	private fun createParticipant(id: String, p: Element): Participant {
		return Participant(
			id, p.getChildContent("nick"), p.getChildContent("jid")?.toBareJID()
		)
	}

	fun retrieveParticipants(channel: BareJID): RequestBuilder<Collection<Participant>, IQ> {
		return pubsubModule.retrieveItem(channel, NODE_PARTICIPANTS).map { r ->
			r.items.map { item -> createParticipant(item.id, item.content!!) }
		}
	}

	fun message(channel: BareJID, message: String): RequestBuilder<Unit, Message> {
		return context.request.message {
			to = channel
			type = MessageType.Groupchat
			body = message
		}
	}

	fun publishInfo(channel: BareJID, info: ChannelInfo): RequestBuilder<Unit, IQ> {
		return pubsubModule.publish(channel, node = "urn:xmpp:mix:nodes:info", itemId = null, payload = info.toElement()).map { }
	}

	fun retrieveInfo(channel: BareJID): RequestBuilder<ChannelInfo?, IQ> {
		return pubsubModule.retrieveItem(channel, node = "urn:xmpp:mix:nodes:info", maxItems = 1).map {
			val item = it.items.firstOrNull()?.content;
			if (item != null) {
				return@map ChannelInfo.fromElement(item)
			} else {
				return@map null
			}
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
		fromChannel: BareJID? = null,
		with: String? = null,
		rsm: RSM.Query? = null,
		start: Instant? = null,
		end: Instant? = null,
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


