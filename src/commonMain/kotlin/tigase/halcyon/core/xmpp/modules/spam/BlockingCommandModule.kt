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
package tigase.halcyon.core.xmpp.modules.spam

import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.modules.AbstractXmppIQModule
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.toBareJID

enum class Reason {

	NotSpecified,

	/**
	 * Used for reporting a JID that is sending unwanted messages.
	 */
	Spam,

	/**
	 * Used for reporting general abuse.
	 */
	Abuse
}

sealed class BlockingCommandEvent : Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.spam.BlockingCommandEvent"
	}

	data class Blocked(val jid: BareJID, val reason: Reason, val text: String?) : BlockingCommandEvent()
	data class Unblocked(val jid: BareJID) : BlockingCommandEvent()
	class UnblockedAll : BlockingCommandEvent()
}

class BlockingCommandModule(override val context: Context) : AbstractXmppIQModule(
	context, TYPE, arrayOf(XMLNS), Criterion.chain(Criterion.name(IQ.NAME), Criterion.xmlns(XMLNS))
) {

	companion object {

		const val XMLNS = "urn:xmpp:blocking"
		const val XMLNS_REPORT = "urn:xmpp:reporting:0"
		const val TYPE = XMLNS
	}

	override fun processGet(element: IQ) = throw XMPPException(ErrorCondition.BadRequest)

	override fun processSet(element: IQ) {
		if (element.from != null) throw XMPPException(ErrorCondition.NotAllowed)
		element.getFirstChild("block")?.let { processBlock(it) }
		element.getFirstChild("unblock")?.let { processUnblock(it) }
	}

	private fun processUnblock(unblock: Element) {
		val items = unblock.children
		if (items.isEmpty()) {
			context.eventBus.fire(BlockingCommandEvent.UnblockedAll())
		} else {
			items.filter { it.name == "item" }.forEach {
				context.eventBus.fire(BlockingCommandEvent.Unblocked(it.attributes["jid"]!!.toBareJID()))
			}
		}
	}

	private fun processBlock(block: Element) {
		block.children.filter { it.name == "item" }.forEach {
			val (reason, text) = it.getChildrenNS("report", XMLNS_REPORT)?.let { report ->
				val text = report.getFirstChild("text")?.value
				val reason = when {
					report.getFirstChild("abuse") != null -> Reason.Abuse
					report.getFirstChild("spam") != null -> Reason.Spam
					else -> Reason.NotSpecified
				}
				Pair(reason, text)
			} ?: Pair<Reason, String?>(Reason.NotSpecified, null)
			context.eventBus.fire(BlockingCommandEvent.Blocked(it.attributes["jid"]!!.toBareJID(), reason, text))
		}
	}

	fun retrieveList(): RequestBuilder<List<BareJID>, IQ> = context.request.iq {
		type = IQType.Get
		"blocklist"{
			xmlns = XMLNS
		}
	}.map { value -> createRetrieveResponse(value) }

	private fun createRetrieveResponse(stanza: IQ): List<BareJID> {
		return stanza.getChildrenNS("blocklist", XMLNS)?.children?.filter { it.name == "item" }
			?.map { it.attributes["jid"]!!.toBareJID() } ?: emptyList()
	}

	fun block(jid: BareJID, reason: Reason = Reason.NotSpecified, text: String? = null): RequestBuilder<Unit, IQ> =
		context.request.iq {
			type = IQType.Set
			"block"{
				xmlns = XMLNS
				"item"{
					attributes["jid"] = jid.toString()
					if (reason != Reason.NotSpecified) {
						"report"{
							xmlns = "urn:xmpp:reporting:0"
							when (reason) {
								Reason.Abuse -> "abuse"{}
								Reason.Spam -> "spam"{}
								else -> throw HalcyonException("Unsupported reason $reason")
							}
							text?.let { "text"{ +it } }
						}
					}
				}
			}
		}.map { Unit }

	fun unblock(vararg jids: BareJID): RequestBuilder<Unit, IQ> = context.request.iq {
		type = IQType.Set
		"unblock"{
			xmlns = XMLNS
			jids.forEach { jid ->
				"item"{
					attributes["jid"] = jid.toString()
				}
			}
		}
	}.map { Unit }

	fun unblockAll(): RequestBuilder<Unit, IQ> = unblock()

}