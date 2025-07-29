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
package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.modules.mix.MIXModule
import tigase.halcyon.core.xmpp.modules.mix.isMixMessage
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.modules.pubsub.isPubSubMessage
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.wrap

/**
 * Event raised when Message stanza is received.
 * @param fromJID JabberID of stanza server.
 * @param stanza received [Message] stanza
 */
data class MessageReceivedEvent(val fromJID: JID?, val stanza: Message) : Event(TYPE) {

    companion object : EventDefinition<MessageReceivedEvent> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.MessageReceivedEvent"
    }
}

@HalcyonConfigDsl
interface MessageModuleConfig

/**
 * Incoming Message stanzas handler. The module is integrated part of XMPP Core protocol.
 */
class MessageModule(override val context: Context) :
    XmppModule,
    MessageModuleConfig {

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.MessageModule")

    override val type = TYPE
    override val criteria: Criteria = Criterion.element(this@MessageModule::isMessage)
    override val features: Array<String>? = null
    // 	override val criteria = Criterion.name(Message.NAME)

    /**
     * Incoming Message stanzas handler. The module is integrated part of XMPP Core protocol.
     */
    companion object : XmppModuleProvider<MessageModule, MessageModuleConfig> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.MessageModule"
        override fun instance(context: Context): MessageModule = MessageModule(context)

        override fun configure(module: MessageModule, cfg: MessageModuleConfig.() -> Unit) =
            module.cfg()
    }

    private fun isMessage(message: Element): Boolean = when {
        context.modules.isRegistered(MIXModule.TYPE) && message.isMixMessage() -> false
        context.modules.isRegistered(PubSubModule.TYPE) && message.isPubSubMessage() -> false
        else -> message.name == Message.NAME
    }

    override fun process(element: Element) {
        val msg: Message = wrap(element)
        context.eventBus.fire(MessageReceivedEvent(msg.from, msg))
    }
}
