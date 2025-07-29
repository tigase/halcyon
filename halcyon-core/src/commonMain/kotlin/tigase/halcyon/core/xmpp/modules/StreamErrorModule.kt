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
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.StreamError

/**
 * XMPP stream error.
 *
 * @property element whole received `<stream:error>` element.
 * @property condition parsed stream error enum to easy check kind of error.
 * @property errorElement error condition element.
 */
data class StreamErrorEvent(
    val element: Element,
    val condition: StreamError,
    val errorElement: Element
) : Event(TYPE) {

    companion object : EventDefinition<StreamErrorEvent> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.StreamErrorEvent"
    }
}

@HalcyonConfigDsl
interface StreamErrorModuleConfig

/**
 * Stream Error Handler. The module is integrated part of XMPP Core protocol.
 */
class StreamErrorModule(override val context: Context) :
    XmppModule,
    StreamErrorModuleConfig {

    /**
     * Stream Error Handler. The module is integrated part of XMPP Core protocol.
     */
    companion object : XmppModuleProvider<StreamErrorModule, StreamErrorModuleConfig> {

        override val TYPE = "StreamErrorModule"
        override fun instance(context: Context): StreamErrorModule = StreamErrorModule(context)

        override fun configure(module: StreamErrorModule, cfg: StreamErrorModuleConfig.() -> Unit) =
            module.cfg()

        const val XMLNS = "urn:ietf:params:xml:ns:xmpp-streams"
    }

    override val type = TYPE
    override val criteria = Criterion.and(
        Criterion.name("error"),
        Criterion.xmlns("http://etherx.jabber.org/streams")
    )
    override val features: Array<String>? = null

    private fun getByElementName(name: String): StreamError {
        for (e in StreamError.values()) {
            if (e.elementName == name) {
                return e
            }
        }
        return StreamError.UNKNOWN_STREAM_ERROR
    }

    override fun process(element: Element) {
        val c = element.getChildrenNS(XMLNS).first()
        val e = getByElementName(c.name)

        context.eventBus.fire(StreamErrorEvent(element, e, c))
    }
}
