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
package tigase.halcyon.core.xmpp.modules.sims

import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.modules.AbstractXmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

@HalcyonConfigDsl
interface ReferenceModuleConfig

class ReferenceModule(context: Context) :
    AbstractXmppModule(context, TYPE, arrayOf(XMLNS)),
    ReferenceModuleConfig {

    companion object : XmppModuleProvider<ReferenceModule, ReferenceModuleConfig> {

        const val XMLNS = "urn:xmpp:reference:0"
        override val TYPE = XMLNS

        override fun instance(context: Context): ReferenceModule = ReferenceModule(context)

        override fun configure(module: ReferenceModule, cfg: ReferenceModuleConfig.() -> Unit) =
            module.cfg()
    }

    override fun process(element: Element) =
        throw XMPPException(ErrorCondition.FeatureNotImplemented)
}
