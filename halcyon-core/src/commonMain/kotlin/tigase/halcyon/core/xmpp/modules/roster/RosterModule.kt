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
package tigase.halcyon.core.xmpp.modules.roster

import kotlinx.serialization.Serializable
import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.modules.AbstractXmppIQModule
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.requests.RequestBuilder

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toBareJID

sealed class RosterEvent(val itemElement: Element, val item: RosterItem) : Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.roster.RosterEvent"
	}

	class ItemAdded(element: Element, item: RosterItem) : RosterEvent(element, item)
	class ItemUpdated(element: Element, val oldItem: RosterItem, item: RosterItem) : RosterEvent(element, item)
	class ItemRemoved(element: Element, item: RosterItem) : RosterEvent(element, item)
}

class RosterLoadedEvent : Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.roster.RosterLoadedEvent"
	}

}

enum class Subscription(val value: String) { Both("both"),
	From("from"),
	None("none"),
	Remove("remove"),
	To("to")
}

enum class Ask(val value: String) { Subscribe("subscribe")
}

interface RosterItemAnnotationProcessor {

	fun prepareRosterGetRequest(stanza: IQ)

	fun processRosterItem(item: Element): RosterItemAnnotation?

}

interface RosterItemAnnotation

@Serializable
data class RosterItem(
	val jid: BareJID,
	val name: String?,
	val groups: List<String> = emptyList(),
	val ask: Ask? = null,
	val subscription: Subscription? = null,
	val approved: Boolean? = null,
	val annotations: Array<RosterItemAnnotation> = emptyArray()
)

data class RosterResponse(val version: String?)

class RosterModule(context: Context) : AbstractXmppIQModule(
	context, TYPE, emptyArray(), Criterion.chain(
		Criterion.name(IQ.NAME), Criterion.xmlns(XMLNS)
	)
) {

	private val log = Logger("com.example.modules.roster.RosterModule")

	companion object {

		const val XMLNS = "jabber:iq:roster"
		const val TYPE = XMLNS
	}

	var store: RosterStore = DefaultRosterStore()

	fun rosterGet(): RequestBuilder<RosterResponse, IQ> {
		val iq = iq {
			type = IQType.Get
			"query"{
				xmlns = XMLNS
				store.getVersion()?.let {
					attribute("ver", it)
				}
			}
		}
		updateRequest(iq)
		return context.request.iq(iq).map {
			val result =
				it.getChildrenNS("query", XMLNS)?.let(this@RosterModule::processQueryResponse) ?: RosterResponse(null)
			context.eventBus.fire(RosterLoadedEvent())
			result
		}
	}

	private fun updateRequest(iq: IQ) {
		context.modules.getModules().filter { it is RosterItemAnnotationProcessor }.forEach {
			(it as RosterItemAnnotationProcessor).prepareRosterGetRequest(iq)
		}
	}

	private fun createItem(rosterItem: RosterItem): Element = element("item") {
		attribute("jid", rosterItem.jid.toString())
		rosterItem.name?.let { attribute("name", it) }
//		rosterItem.ask?.let { attribute("ask", it.value) }
//		rosterItem.subscription?.let { attribute("subscription", it.value) }
//		rosterItem.approved?.let {
//			attribute(
//				"approved", if (it) {
//					"1"
//				} else {
//					"0"
//				}
//			)
//		}
		rosterItem.groups.forEach { groupName ->
			"group"{
				+groupName
			}
		}
	}

	private fun processQueryResponse(query: Element): RosterResponse {
		log.fine("Processing roster data")
		val ver = query.attributes["ver"]
		query.getChildren("item").map { item ->
			val ri = parseItem(item)
			processRosterItem(item, ri)
		}
		ver?.let { store.setVersion(it) }
		return RosterResponse(ver)
	}

	private fun processRosterItem(itemElement: Element, item: RosterItem) {
		val oldItem: RosterItem? = store.getItem(item.jid)
		if (oldItem != null && item.subscription == Subscription.Remove) {
			log.fine("Remove item ${item.jid}")
			store.removeItem(item.jid)
			context.eventBus.fire(RosterEvent.ItemRemoved(itemElement, item))
		} else if (oldItem == null && item.subscription != Subscription.Remove) {
			log.fine("Add item ${item.jid}")
			store.addItem(item.jid, item)
			context.eventBus.fire(RosterEvent.ItemAdded(itemElement, item))
		} else if (oldItem != null && item.subscription != Subscription.Remove) {
			log.fine("Update item ${item.jid}")
			store.updateItem(item.jid, item)
			context.eventBus.fire(RosterEvent.ItemUpdated(itemElement, oldItem, item))
		} else {
			log.fine("Ignore item ${item.jid}")
		}
	}

	private fun parseItem(item: Element): RosterItem {
		val jid = item.attributes["jid"]?.toBareJID() ?: throw XMPPException(
			ErrorCondition.BadRequest, "Missing JID in roster item."
		)
		val name = item.attributes["name"]
		val subscription = item.attributes["subscription"]?.let { sname ->
			Subscription.values().firstOrNull { s -> s.value == sname }
				?: throw XMPPException(ErrorCondition.BadRequest)
		}
		val ask = item.attributes["ask"]?.let { sname ->
			Ask.values().firstOrNull { s -> s.value == sname } ?: throw XMPPException(ErrorCondition.BadRequest)
		}
		val approved = when (item.attributes["approved"]) {
			"1", "true" -> true
			"0", "false" -> false
			null -> null
			else -> throw XMPPException(ErrorCondition.BadRequest, "Unknown value of approved field.")
		}
		val groups = item.getChildren("group").map { it.value ?: "" }

		val annotations = createAnnotations(item)

		return RosterItem(jid, name, groups, ask, subscription, approved, annotations)
	}

	private fun createAnnotations(item: Element): Array<RosterItemAnnotation> {
		return context.modules.getModules().filter { it is RosterItemAnnotationProcessor }
			.mapNotNull { (it as RosterItemAnnotationProcessor).processRosterItem(item) }.toTypedArray()
	}

	override fun processGet(element: IQ) = throw XMPPException(ErrorCondition.NotAllowed)

	override fun processSet(element: IQ) {
		val boundJID = context.modules.getModule<BindModule>(BindModule.TYPE).boundJID
			?: throw HalcyonException("Session is not bound. Cannot process roster request.")
		val from = element.from
		if (from != null && from.bareJID != boundJID.bareJID) {
			throw XMPPException(ErrorCondition.NotAllowed)
		}
		element.getChildrenNS("query", XMLNS)?.let(this@RosterModule::processQueryResponse)
	}

	/**
	 * Add or update roster item.
	 */
	fun addItem(vararg items: RosterItem): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			type = IQType.Set
			"query"{
				xmlns = XMLNS
				items.forEach {
					addChild(createItem(it))
				}
			}
		}.map { Unit }
	}

	fun deleteItem(vararg jids: BareJID): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			type = IQType.Set
			"query"{
				xmlns = XMLNS
				jids.forEach { jid ->
					"item"{
						attribute("jid", jid.toString())
						attribute("subscription", "remove")
					}
				}
			}
		}.map { Unit }
	}

	fun getAllItems(): List<RosterItem> = store.getAllItems()

}