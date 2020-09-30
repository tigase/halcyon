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
package tigase.halcyon.core.xmpp.modules.avatar

import kotlinx.serialization.Serializable
import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.RequestBuilder

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubEventReceivedEvent
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.Message

data class UserAvatarUpdatedEvent(val jid: BareJID, val avatarId: String) : Event(TYPE) { companion object {

	const val TYPE = "tigase.halcyon.core.xmpp.modules.avatar.UserAvatarUpdatedEvent"
}
}

class UserAvatarModule(override val context: Context) : XmppModule {

	companion object {

		const val TYPE = "urn:xmpp:avatar"
		const val XMLNS_DATA = "urn:xmpp:avatar:data"
		const val XMLNS_METADATA = "urn:xmpp:avatar:metadata"
	}

	private val log = Logger("tigase.halcyon.core.xmpp.modules.avatar.UserAvatarModule")

	override val criteria: Criteria? = null
	override val features: Array<String> = arrayOf("$XMLNS_METADATA+notify")
	override val type: String = TYPE

	var store: UserAvatarStore = object : UserAvatarStore {

		private val items = mutableMapOf<String, Avatar>()

		override fun store(userJID: BareJID, avatarID: String?, data: Avatar?) {
			if (avatarID == null) return
			if (data == null) {
				items.remove(avatarID)
			} else {
				items[avatarID] = data
			}
		}

		override fun load(userJID: BareJID, avatarID: String): Avatar? = items[avatarID]

		override fun isStored(userJID: BareJID, avatarID: String): Boolean = items.containsKey(avatarID)
	}

	lateinit var pubSubModule: PubSubModule

	override fun initialize() {
		this.pubSubModule = context.modules.getModule(PubSubModule.TYPE)
		context.eventBus.register<PubSubEventReceivedEvent>(PubSubEventReceivedEvent.TYPE) { event ->
			if (event.nodeName == XMLNS_METADATA) {
				event.items.forEach {
					val id = it.attributes["id"]
					val metadata = it.getChildrenNS("metadata", XMLNS_METADATA)
					if (id != null && metadata != null) processMetadataItem(event.stanza, id, metadata)
				}
			}
		}
	}

	private fun parseInfo(info: Element): AvatarInfo {
		return AvatarInfo(info.attributes["bytes"]!!.toInt(),
						  info.attributes["height"]?.toInt(),
						  info.attributes["id"]!!,
						  info.attributes["type"]!!,
						  info.attributes["url"],
						  info.attributes["width"]?.toInt())
	}

	private fun processMetadataItem(stanza: Message, avatarID: String, metadata: Element) {
		val userJID = stanza.from?.bareJID ?: return
		val info = metadata.getFirstChild("info")?.let { parseInfo(it) }
		if (info == null) {
			store.store(userJID, null, null)
			return
		}

		val stored = store.isStored(userJID, avatarID)
		if (!stored) {
			retrieveAvatar(userJID.toString().toJID(), avatarID).response {
				if (it.isSuccess) {
					log.fine("Storing UserAvatar data $avatarID" + it.getOrNull())
					val avatar = it.getOrNull()?.let {
						if (it.base64Data == null) {
							null
						} else {
							Avatar(info, it)
						}
					}
					store.store(userJID, avatarID, avatar)
					log.fine("Stored data! $userJID")
					context.eventBus.fire(UserAvatarUpdatedEvent(userJID, avatarID))
				}
			}.send()
		} else {
			context.eventBus.fire(UserAvatarUpdatedEvent(userJID, avatarID))
		}
	}

	@Serializable
	data class Avatar(val info: AvatarInfo, val data: AvatarData)

	@Serializable
	data class AvatarData(val id: String, val base64Data: String?)

	@Serializable
	data class AvatarInfo(
		val bytes: Int, val height: Int?, val id: String, val type: String, val url: String?, val width: Int?
	)

	fun retrieveAvatar(jid: JID, avatarID: String): RequestBuilder<AvatarData, IQ> {
		val x = pubSubModule.retrieveItem(JID.parse(jid.bareJID.toString()), XMLNS_DATA, avatarID).map { response ->
			val item = response.items.first()
			val data = item.content!!.value
			AvatarData(avatarID, data)
		}
		return x
	}

	override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

	fun publish(data: AvatarData): RequestBuilder<PubSubModule.PublishingInfo, IQ> {
		val payload = element("data") {
			xmlns = XMLNS_DATA
			+data.base64Data!!
		}
		return pubSubModule.publish(null, XMLNS_DATA, data.id, payload)
	}

	fun publish(data: AvatarInfo): RequestBuilder<PubSubModule.PublishingInfo, IQ> {
		val payload = element("metadata") {
			xmlns = XMLNS_METADATA
			"info"{
				attribute("id", data.id)
				attribute("type", data.type)
				attribute("bytes", "${data.bytes}")
				data.height?.let { attribute("height", "$it") }
				data.width?.let { attribute("width", "$it") }
				data.url?.let { attribute("url", it) }
			}
		}
		return pubSubModule.publish(null, XMLNS_METADATA, data.id, payload)
	}

}
