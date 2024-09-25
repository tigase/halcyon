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
import tigase.halcyon.core.modules.AbstractXmppIQModule
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toBareJID

/**
 * Configuration of [ExternalServiceDiscoveryModule].
 */
@HalcyonConfigDsl
interface ExternalServiceDiscoveryModuleConfig

/**
 * Module is implementing External Service Discovery ([XEP-0215](https://xmpp.org/extensions/xep-0215.html)).
 *
 */
class ExternalServiceDiscoveryModule(context: Context): ExternalServiceDiscoveryModuleConfig, AbstractXmppIQModule(
    context, TYPE, emptyArray(), Criterion.chain()
) {
    companion object : XmppModuleProvider<ExternalServiceDiscoveryModule, ExternalServiceDiscoveryModuleConfig> {
        val XMLNS = "urn:xmpp:extdisco:2";
        override val TYPE = XMLNS;
        override fun configure(module: ExternalServiceDiscoveryModule, cfg: ExternalServiceDiscoveryModuleConfig.() -> Unit) = module.cfg()
        override fun instance(context: Context): ExternalServiceDiscoveryModule = ExternalServiceDiscoveryModule(context)
    }

    data class Service(
        val expires: String?,
        val host: String,
        val name: String?,
        val password: String?,
        val port: Int?,
        val restricted: Boolean,
        val transport: Transport?,
        val type: String,
        val username: String?
    ) {

        enum class Transport {
            tcp, udp
        }

        companion object {
            fun parse(el: Element): Service? {
                if (el.name != "service") return null;
                val type = el.attributes["type"] ?: return null
                val host = el.attributes["host"] ?: return null
                val name = el.attributes["name"]
                val port = el.attributes["port"]?.toInt()
                val transport = el.attributes["transport"]?.lowercase()?.let(Transport::valueOf)
                val username = el.attributes["username"]
                val password = el.attributes["password"];
                val restricted = el.attributes["restricted"]?.let { v -> v == "1" || v == "true" } ?: false

                return Service(
                    type = type,
                    name = name,
                    host = host,
                    port = port,
                    transport = transport,
                    username = username,
                    password = password,
                    restricted = restricted,
                    expires = el.attributes["expires"]
                )
            }
        }

    }

    fun discover(jid: JID? = null, type: String?): RequestBuilder<List<Service>, IQ> {
        val stanza = iq {
            this.type = IQType.Get
            this.to = jid ?: context.boundJID?.domain?.toBareJID()
            "services" {
                xmlns = XMLNS
                type?.let {
                    attribute("type", it)
                }
            }
        }
        return context.request.iq(stanza).map {
            it.getChildrenNS("services", XMLNS)?.children?.map(Service::parse)?.filterNotNull() ?: emptyList()
        }
    }


    override fun processGet(element: IQ) {
       // nothing to do...
    }

    override fun processSet(element: IQ) {
        // nothing to do...
    }
}