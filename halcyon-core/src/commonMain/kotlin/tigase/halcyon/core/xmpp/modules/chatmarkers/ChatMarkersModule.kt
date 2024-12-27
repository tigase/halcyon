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
package tigase.halcyon.core.xmpp.modules.chatmarkers

import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.modules.*
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.modules.uniqueId.getOriginID
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.MessageNode
import tigase.halcyon.core.xmpp.stanzas.MessageType
import tigase.halcyon.core.xmpp.stanzas.wrap

data class ChatMarkerEvent(val jid: JID, val msgId: String, val stanza: Message, val marker: ChatMarkersModule.Marker) :
	Event(TYPE) {

	companion object : EventDefinition<ChatMarkerEvent> {

		override val TYPE = "tigase.halcyon.core.xmpp.modules.chatmarkers.ChatMarkerEvent"
	}
}


@HalcyonConfigDsl
interface ChatMarkersModuleConfig {

	enum class Mode {

		/**
		 * Makes markable all outgoing messages.
		 */
		All,

		/**
		 * If message is send to full JID, then message will be markable only if recipient supports Chat Markers.
		 * Support of Chat Markers is determined by Entity Capabilities.
		 * If message is send to bare JID, then always will be markable.
		 */
		Auto,

		/**
		 * Adding markable is turned off.
		 */
		Off
	}

	/**
	 * Define, how library should add Chat Markers request to outgoing messages.
	 */
	var mode: Mode

	/**
	 * If ``true`` then library will automatically (and immediately) send chat marker ``Received`` when message will
	 * be received by client.
	 */
	var autoSendReceived: Boolean

}


/**
 * Module is implementing Chat Markers ([XEP-0333](https://xmpp.org/extensions/xep-0333.html)).
 *
 */
class ChatMarkersModule(override val context: Context) : XmppModule, StanzaInterceptor, ChatMarkersModuleConfig {

	enum class Marker(val xmppValue: String) {

		/**
		 * Message has been received by a client
		 */
		Received("received"),

		/**
		 * Message has been displayed to a user in a active chat and not in a system notification
		 */
		Displayed("displayed"),

		/**
		 * Message has been acknowledged by some user interaction e.g. pressing an acknowledgement button.
		 */
		Acknowledged("acknowledged")
	}

	/**
	 * Module is implementing Chat Markers ([XEP-0333](https://xmpp.org/extensions/xep-0333.html)).
	 *
	 */
	companion object : XmppModuleProvider<ChatMarkersModule, ChatMarkersModuleConfig> {

		const val XMLNS = "urn:xmpp:chat-markers:0"
		override val TYPE = XMLNS

		override fun instance(context: Context): ChatMarkersModule = ChatMarkersModule(context)

		override fun configure(module: ChatMarkersModule, cfg: ChatMarkersModuleConfig.() -> Unit) = module.cfg()

		override fun doAfterRegistration(module: ChatMarkersModule, moduleManager: ModulesManager) =
			moduleManager.registerInterceptors(arrayOf(module))


	}

	override val criteria: Criteria? = null
	override val features: Array<String> = arrayOf(XMLNS)
	override val type = TYPE

	override var mode: ChatMarkersModuleConfig.Mode = ChatMarkersModuleConfig.Mode.Auto
	override var autoSendReceived = false


	override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

	/**
	 *  Prepares message mark request.
	 *
	 * @param to recipient of marker.
	 * @param id marked stanza id
	 * @param marker marker
	 */
	fun markMessage(to: JID, id: String, marker: Marker): RequestBuilder<Unit, Message> {
		return context.request.message(true) {
			this.to = to
			marker.xmppValue {
				this.xmlns = XMLNS
				this.attributes["id"] = id
			}
		}
	}

	override fun afterReceive(element: Element): Element {
		if (element.name != Message.NAME) return element
		if (element.attributes["type"] == MessageType.Error.value) return element
		val from = element.attributes["from"]?.toJID() ?: return element

		val command = element.getChildrenNS(XMLNS).firstOrNull() ?: return element
		when (command.name) {
			"markable" -> {
				if (autoSendReceived) element.getOriginID() ?: element.attributes["id"]?.let { id ->
					markMessage(from, id, Marker.Received).send()
				}
			}

			Marker.Received.xmppValue -> {
				val id = command.attributes["id"]
				if (id != null) context.eventBus.fire(ChatMarkerEvent(from, id, wrap(element), Marker.Received))
			}

			Marker.Acknowledged.xmppValue -> {
				val id = command.attributes["id"]
				if (id != null) context.eventBus.fire(ChatMarkerEvent(from, id, wrap(element), Marker.Acknowledged))
			}

			Marker.Displayed.xmppValue -> {
				val id = command.attributes["id"]
				if (id != null) context.eventBus.fire(ChatMarkerEvent(from, id, wrap(element), Marker.Displayed))
			}

			else -> throw XMPPException(
				ErrorCondition.FeatureNotImplemented, "Unsupported chat marker '${command.name}'"
			)
		}
		return element
	}

	private fun isMarkable(to: JID): Boolean = when (mode) {
		ChatMarkersModuleConfig.Mode.Off -> false
		ChatMarkersModuleConfig.Mode.All -> true
		ChatMarkersModuleConfig.Mode.Auto -> when (to.resource) {
			null -> true
			else -> isChatMarkerSupported(to)
		}
	}

	private fun isChatMarkerSupported(jid: JID): Boolean {
		val presenceModule = context.modules.getModuleOrNull<PresenceModule>(PresenceModule.TYPE) ?: return false
		val capsModule =
			context.modules.getModuleOrNull<EntityCapabilitiesModule>(EntityCapabilitiesModule.TYPE) ?: return false
		val p = presenceModule.getPresenceOf(jid) ?: return false
		val caps = capsModule.getCapabilities(p) ?: return false
		return caps.features.contains(XMLNS)
	}

	override fun beforeSend(element: Element): Element {
		if (element.name != Message.NAME) return element
		val to = element.attributes["to"]?.toJID() ?: return element
		if (element.getFirstChild("body") == null) return element

		if (isMarkable(to)) element.add(tigase.halcyon.core.xml.element("markable") { xmlns = XMLNS })

		return element
	}
}

/**
 * Representation of Chat Marker.
 */
sealed class ChatMarker(
	/**
	 * Chat marker type.
	 */
	val marker: ChatMarkersModule.Marker,
	/**
	 * Identifier of the message to which the marker applies.
	 */
	val originId: String,
	/**
	 * Sender of message with marker.
	 */
	val sender: JID
) {

	/**
	 * Message has been received by a client.
	 */
	class Received(originId: String, sender: JID) : ChatMarker(ChatMarkersModule.Marker.Received, originId, sender)

	/**
	 * Message has been acknowledged by some user interaction e.g. pressing an acknowledgement button.
	 */
	class Acknowledged(originId: String, sender: JID) :
		ChatMarker(ChatMarkersModule.Marker.Acknowledged, originId, sender)

	/**
	 * Message has been displayed to a user in a active chat and not in a system notification
	 */
	class Displayed(originId: String, sender: JID) : ChatMarker(ChatMarkersModule.Marker.Displayed, originId, sender)

	override fun toString(): String {
		return "ChatMarker(sender=$sender, marker=$marker, originId='$originId')"
	}
}

/**
 * Add Chat Marker request to stanza.
 */
fun MessageNode.markable() = this.element.add(tigase.halcyon.core.xml.element("markable") {
	xmlns = ChatMarkersModule.XMLNS
})

/**
 * Checks if received stanza contains request for Chat Marker.
 */
fun Element?.isChatMarkerRequested(): Boolean =
	this != null && this.getChildrenNS("markable", ChatMarkersModule.XMLNS) != null

/**
 * Returns chat marker attached to received stanza or `null` if not provided.
 */
fun Element.getChatMarkerOrNull(): ChatMarker? {
	if (this.name != Message.NAME) return null
	if (this.attributes["type"] == MessageType.Error.value) return null
	val from = this.attributes["from"]?.toJID() ?: return null
	val command = this.getChildrenNS(ChatMarkersModule.XMLNS).firstOrNull() ?: return null
	val id = command.attributes["id"] ?: return null

	return when (command.name) {
		ChatMarkersModule.Marker.Displayed.xmppValue -> ChatMarker.Displayed(id, from)
		ChatMarkersModule.Marker.Acknowledged.xmppValue -> ChatMarker.Acknowledged(id, from)
		ChatMarkersModule.Marker.Received.xmppValue -> ChatMarker.Received(id, from)
		else -> null
	}
}