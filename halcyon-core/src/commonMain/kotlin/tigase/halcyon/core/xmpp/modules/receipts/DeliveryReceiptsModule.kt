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
package tigase.halcyon.core.xmpp.modules.receipts

import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.modules.*
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.MessageNode
import tigase.halcyon.core.xmpp.stanzas.MessageType
import tigase.halcyon.core.xmpp.stanzas.message
import tigase.halcyon.core.xmpp.toJID

/**
 * Event fired when a delivery receipt was received.
 * @param jid JabberID of sender
 * @param msgId identifier of the message to which the received delivery receipt refers.
 */
data class MessageDeliveryReceiptEvent(val jid: JID, val msgId: String) : Event(TYPE) {

    companion object : EventDefinition<MessageDeliveryReceiptEvent> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.receipts.MessageDeliveryReceiptEvent"
    }
}

@HalcyonConfigDsl
interface DeliveryReceiptsModuleConfig {

    enum class Mode {
        /**
         * Add request to all outgoing messages.
         */
        All,

        /**
         * Do not add request.
         */
        Off
    }

    /**
     * If ``true`` then library will automatically (and immediately) send delivery receipt when message will
     * be received by client.
     */
    var autoSendReceived: Boolean

    /**
     * Define, how module should add delivery receipt request to outgoing messages.
     */
    var mode: Mode

}

/**
 * Module is implementing Message Delivery Receipts ([XEP-0184](https://xmpp.org/extensions/xep-0184.html)).
 *
 */
class DeliveryReceiptsModule(override val context: Context) : XmppModule, DeliveryReceiptsModuleConfig {

    /**
     * Module is implementing Message Delivery Receipts ([XEP-0184](https://xmpp.org/extensions/xep-0184.html)).
     *
     */
    companion object : XmppModuleProvider<DeliveryReceiptsModule, DeliveryReceiptsModuleConfig> {

        const val XMLNS = "urn:xmpp:receipts"
        override val TYPE = XMLNS

        override fun instance(context: Context): DeliveryReceiptsModule = DeliveryReceiptsModule(context)

        override fun configure(module: DeliveryReceiptsModule, cfg: DeliveryReceiptsModuleConfig.() -> Unit) =
            module.cfg()

        override fun doAfterRegistration(module: DeliveryReceiptsModule, moduleManager: ModulesManager) {
            moduleManager.registerIncomingFilter(createFilter { element, chain ->
                element?.let {
                    module.afterReceive(it)
                    chain.doFilter(it)
                }
            })

            moduleManager.registerOutgoingFilter(createFilter { element, chain ->
                element?.let {
                    module.beforeSend(it)
                    chain.doFilter(it)
                }
            })
        }

    }

    override val criteria: Criteria? = null
    override val features: Array<String> = arrayOf(XMLNS)
    override val type = TYPE

    override var autoSendReceived = false
    override var mode: DeliveryReceiptsModuleConfig.Mode = DeliveryReceiptsModuleConfig.Mode.All

    override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

    fun afterReceive(element: Element): Element {
        if (element.name != Message.NAME) return element
        if (element.attributes["type"] == MessageType.Error.value) return element
        val from = element.attributes["from"]?.toJID() ?: return element

        element.getReceiptReceivedID()?.let { id -> context.eventBus.fire(MessageDeliveryReceiptEvent(from, id)) }

        if (autoSendReceived) element.getChildrenNS("request", XMLNS)?.let {
            element.attributes["id"]?.let { id ->
                val resp = message {
                    element.attributes["from"]?.let {
                        attribute("to", it)
                    }
                    element.attributes["type"]?.let {
                        attribute("type", it)
                    }
                    "received" {
                        xmlns = XMLNS
                        attribute("id", id)
                    }
                }
                context.writer.writeDirectly(resp)
            }
        }
        return element
    }

    fun received(jid: JID, originId: String): RequestBuilder<Unit, Message> = context.request.message(message {
        to = jid
        "received" {
            xmlns = XMLNS
            attribute("id", originId)
        }
    }, true)

    fun beforeSend(element: Element): Element {
        if (element.name != Message.NAME) return element
        if (element.attributes["type"] == MessageType.Groupchat.value) return element
        if (element.attributes["id"] == null) return element
        if (element.getChildrenNS("request", XMLNS) != null) return element
        if (element.getChildrenNS("received", XMLNS) != null) return element
        if (element.getFirstChild("body") == null) return element

        if (mode == DeliveryReceiptsModuleConfig.Mode.All) element.add(element("request") {
            xmlns = XMLNS
        })
        return element
    }
}

/**
 * Add delivery receipt request to stanza.
 */
fun MessageNode.deliveryReceiptRequest() = this.element.add(tigase.halcyon.core.xml.element("request") {
    xmlns = DeliveryReceiptsModule.XMLNS
})

/**
 * Checks if received stanza contains request for delivery receipt.
 */
fun Element?.isDeliveryReceiptRequested(): Boolean =
    this != null && this.getChildrenNS("request", DeliveryReceiptsModule.XMLNS) != null

/**
 * Returns the identifier of the message to which the delivery receipt refers, or `null` if element doesn't contain a delivery receipt.
 */
fun Element.getReceiptReceivedID(): String? {
    return this.getChildrenNS("received", DeliveryReceiptsModule.XMLNS)?.let { it.attributes["id"] }
}