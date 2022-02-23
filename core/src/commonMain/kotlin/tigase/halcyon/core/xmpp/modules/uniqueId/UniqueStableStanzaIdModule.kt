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
package tigase.halcyon.core.xmpp.modules.uniqueId

import tigase.halcyon.core.Context
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.HasInterceptors
import tigase.halcyon.core.modules.StanzaInterceptor
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.stanzas.Message

class UniqueStableStanzaIdModule(override val context: Context) : XmppModule, HasInterceptors, StanzaInterceptor {

	companion object {

		const val XMLNS = "urn:xmpp:sid:0"
		const val TYPE = XMLNS
	}

	override val type = TYPE
	override val criteria: Criteria? = null
	override val features = arrayOf(XMLNS)
	override val stanzaInterceptors: Array<StanzaInterceptor> = arrayOf(this)

	override fun initialize() {}

	override fun process(element: Element) = throw XMPPException(ErrorCondition.BadRequest)

	override fun afterReceive(element: Element): Element = element

	override fun beforeSend(element: Element): Element {
		if (element.name != Message.NAME) return element
		if (element.attributes["id"] == null) return element
		if (element.getChildrenNS("origin-id", XMLNS) != null) return element

		element.add(tigase.halcyon.core.xml.element("origin-id") {
			xmlns = XMLNS
			attributes["id"] = element.attributes["id"]
		})

		return element
	}

	fun getStanzaID(element: Element): String? {
		val jid = context.modules.getModuleOrNull<BindModule>(BindModule.TYPE)?.boundJID?.bareJID ?: return null
		return element.getStanzaIDBy(jid)
	}

}

fun Element.getStanzaIDBy(by: BareJID): String? {
	val stanzaId = this.children.firstOrNull {
		it.name == "stanza-id" && it.xmlns == UniqueStableStanzaIdModule.XMLNS && it.attributes["by"] == by.toString()
	} ?: return null
	return stanzaId.attributes["id"]
}

fun Element.getOriginID(): String? =
	this.getChildrenNS("origin-id", UniqueStableStanzaIdModule.XMLNS)?.attributes?.get("id")

