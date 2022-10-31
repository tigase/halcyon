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
package tigase.halcyon.core.xmpp.modules.carbons

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.ConfigurationException
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.getFromAttr
import tigase.halcyon.core.xmpp.modules.MessageModule
import tigase.halcyon.core.xmpp.modules.auth.*
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.asStanza

sealed class CarbonEvent(@Suppress("unused") val fromJID: JID?, val stanza: Message) : Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.carbons.CarbonEvent"
	}

	class Sent(fromJID: JID?, stanza: Message) : CarbonEvent(fromJID, stanza)
	class Received(fromJID: JID?, stanza: Message) : CarbonEvent(fromJID, stanza)
}

interface MessageCarbonsModuleConfig

class MessageCarbonsModule(override val context: Context, private val forwardHandler: (Message) -> Unit) : XmppModule,
																										   InlineProtocol,
																										   MessageCarbonsModuleConfig {

	override val type = TYPE
	override val criteria = Criterion.chain(Criterion.name(Message.NAME), Criterion.xmlns(XMLNS))
	override val features: Array<String> = arrayOf(XMLNS)

	private var messageModule: MessageModule? = null

	companion object : XmppModuleProvider<MessageCarbonsModule, MessageCarbonsModuleConfig> {

		const val XMLNS = "urn:xmpp:carbons:2"
		override val TYPE = XMLNS
		private const val FORWARD_XMLNS = "urn:xmpp:forward:0"

		override fun instance(context: Context): MessageCarbonsModule {
			if (!(context is AbstractHalcyon)) throw ConfigurationException("Cannot create instance of MessageCarbonModule. Unsupported type of context.")
			return MessageCarbonsModule(context, context::processReceivedXmlElement)
		}

		override fun configure(module: MessageCarbonsModule, cfg: MessageCarbonsModuleConfig.() -> Unit) = module.cfg()

	}

	override fun initialize() {
		this.messageModule = context.modules.getModuleOrNull(MessageModule.TYPE)
	}

	override fun process(element: Element) {
		val ownJid = context.boundJID?.bareJID
		val from = element.getFromAttr()
		if (from != null && from.bareJID != ownJid) throw XMPPException(ErrorCondition.NotAcceptable)
		element.getChildrenNS(XMLNS)
			.firstOrNull()
			?.let {
				when (it.name) {
					"sent" -> processSent(it)
					"received" -> processReceived(it)
					else -> throw XMPPException(ErrorCondition.BadRequest)
				}
			}
	}

	@Suppress("unused")
	fun enable(): RequestBuilder<Unit, IQ> = context.request.iq {
		type = IQType.Set
		"enable" {
			xmlns = XMLNS
		}
	}
		.map { }

	@Suppress("unused")
	fun disable(): RequestBuilder<Unit, IQ> = context.request.iq {
		type = IQType.Set
		"disable" {
			xmlns = XMLNS
		}
	}
		.map { }

	private fun processSent(carbon: Element) {
		val msg = carbon.getChildrenNS("forwarded", FORWARD_XMLNS)
			?.getChildren(Message.NAME)
			?.firstOrNull()
			?.asStanza<Message>() ?: return

		messageModule?.process(msg)
//		forwardHandler?.invoke(msg)
		context.eventBus.fire(CarbonEvent.Sent(msg.from, msg))
	}

	private fun processReceived(carbon: Element) {
		val msg = carbon.getChildrenNS("forwarded", FORWARD_XMLNS)
			?.getChildren(Message.NAME)
			?.firstOrNull()
			?.asStanza<Message>() ?: return

		forwardHandler.invoke(msg)
		context.eventBus.fire(CarbonEvent.Received(msg.from, msg))
	}

	override fun featureFor(features: InlineFeatures, stage: InlineProtocolStage): Element? {
		return if (stage == InlineProtocolStage.AfterBind && features.supports(XMLNS)) {
			element("enable") { xmlns = XMLNS }
		} else null
	}

	override fun process(response: InlineResponse) {
	}

}
