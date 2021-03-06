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
package tigase.halcyon.core.xmpp.modules.chatmarkers

import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.HasInterceptors
import tigase.halcyon.core.modules.StanzaInterceptor
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.MessageType
import tigase.halcyon.core.xmpp.toJID

data class ChatMarkerEvent(val jid: JID, val msgId: String, val marker: ChatMarkersModule.Marker) : Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.chatmarkers.ChatMarkerEvent"
	}
}

/**
 * Chat Markers module.
 *
 *
 */
class ChatMarkersModule(override val context: Context) : XmppModule, HasInterceptors, StanzaInterceptor {

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

	companion object {

		const val XMLNS = "urn:xmpp:chat-markers:0"
		const val TYPE = XMLNS
	}

	override val criteria: Criteria? = null
	override val features: Array<String> = arrayOf(XMLNS)
	override val type = TYPE
	override val stanzaInterceptors: Array<StanzaInterceptor> = arrayOf(this)

	var mode: Mode = Mode.Auto

	override fun initialize() {

	}

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
				val id = element.attributes["id"]
				if (id != null) markMessage(from, id, Marker.Received).send()
			}
			Marker.Received.xmppValue -> {
				val id = command.attributes["id"]
				if (id != null) context.eventBus.fire(ChatMarkerEvent(from, id, Marker.Received))
			}
			Marker.Acknowledged.xmppValue -> {
				val id = command.attributes["id"]
				if (id != null) context.eventBus.fire(ChatMarkerEvent(from, id, Marker.Acknowledged))
			}
			Marker.Displayed.xmppValue -> {
				val id = command.attributes["id"]
				if (id != null) context.eventBus.fire(ChatMarkerEvent(from, id, Marker.Displayed))
			}
			else -> throw XMPPException(
				ErrorCondition.FeatureNotImplemented, "Unsupported chat marker '${command.name}'"
			)
		}
		return element
	}

	private fun isMarkable(to: JID): Boolean = when (mode) {
		Mode.Off -> false
		Mode.All -> true
		Mode.Auto -> when (to.resource) {
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

		if (isMarkable(to)) element.add(tigase.halcyon.core.xml.element("markable") { xmlns = XMLNS })

		return element
	}
}

fun Element?.isChatMarkerRequested(): Boolean =
	this != null && this.getChildrenNS("markable", ChatMarkersModule.XMLNS) != null