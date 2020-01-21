package tigase.halcyon.core.xmpp.modules.roster

import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.modules.AbstractXmppIQModule
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.requests.IQRequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.toBareJID

sealed class RosterEvent : Event(TYPE) {
	companion object {
		const val TYPE = "com.example.modules.roster.RosterEvent"
	}

	data class ItemAdded(val item: RosterItem) : RosterEvent()
	data class ItemUpdated(val oldItem: RosterItem, val item: RosterItem) : RosterEvent()
	data class ItemRemoved(val item: RosterItem) : RosterEvent()
}

enum class Subscription(val value: String) {
	Both("both"),
	From("from"),
	None("none"),
	Remove("remove"),
	To("to")
}

enum class Ask(val value: String) {
	Subscribe("subscribe")
}

data class RosterItem(
	val jid: BareJID,
	val name: String?,
	val groups: List<String> = emptyList(),
	val ask: Ask? = null,
	val subscription: Subscription? = null,
	val approved: Boolean? = null
)

data class RosterResponse(val version: String?, val items: List<RosterItem>)

class RosterModule(context: Context) : AbstractXmppIQModule(
	context, TYPE, emptyArray(), Criterion.chain(
		Criterion.name(IQ.NAME), Criterion.xmlns(
			XMLNS
		)
	)
) {

	private val log = Logger("com.example.modules.roster.RosterModule")

	companion object {
		const val XMLNS = "jabber:iq:roster"
		const val TYPE = XMLNS
	}

	var store: RosterStore = DefaultRosterStore()

	fun rosterGet(): IQRequestBuilder<RosterResponse> {
		return context.request.iq<RosterResponse> {
			type = IQType.Get
			"query"{
				xmlns = XMLNS
				store.getVersion()?.let {
					attribute("ver", it)
				}
			}
		}.resultBuilder {
			it.getChildrenNS(
				"query", XMLNS
			)?.let(this@RosterModule::processQueryResponse) ?: RosterResponse(
				null, emptyList()
			)
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
		val items = query.getChildren("item").map { item -> parseItem(item) }
		items.forEach(::processRosterItem)
		ver?.let { store.setVersion(it) }
		return RosterResponse(ver, items)
	}

	private fun processRosterItem(item: RosterItem) {
		val oldItem: RosterItem? = store.getItem(item.jid)
		if (oldItem != null && item.subscription == Subscription.Remove) {
			log.fine("Remove item ${item.jid}")
			store.removeItem(item.jid)
			context.eventBus.fire(
				RosterEvent.ItemRemoved(
					item
				)
			)
		} else if (oldItem == null && item.subscription != Subscription.Remove) {
			log.fine("Add item ${item.jid}")
			store.addItem(item.jid, item)
			context.eventBus.fire(
				RosterEvent.ItemAdded(
					item
				)
			)
		} else if (oldItem != null && item.subscription != Subscription.Remove) {
			log.fine("Update item ${item.jid}")
			store.updateItem(item.jid, item)
			context.eventBus.fire(
				RosterEvent.ItemUpdated(
					oldItem, item
				)
			)
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
			Ask.values().firstOrNull { s -> s.value == sname }
				?: throw XMPPException(ErrorCondition.BadRequest)
		}
		val approved = when (item.attributes["approved"]) {
			"1", "true" -> true
			"0", "false" -> false
			null -> null
			else -> throw XMPPException(ErrorCondition.BadRequest, "Unknown value of approved field.")
		}
		val groups = item.getChildren("group").map { it.value ?: "" }

		return RosterItem(
			jid, name, groups, ask, subscription, approved
		)
	}

	override fun processGet(element: IQ) = throw XMPPException(ErrorCondition.NotAllowed)

	override fun processSet(element: IQ) {
		val boundJID = context.modules.getModule<BindModule>(BindModule.TYPE).boundJID
			?: throw HalcyonException("Session is not bound. Cannot process roster request.")
		val from = element.from
		if (from != null && from.bareJID != boundJID.bareJID) {
			throw XMPPException(ErrorCondition.NotAllowed)
		}
		element.getChildrenNS(
			"query", XMLNS
		)?.let(this@RosterModule::processQueryResponse)
	}

	/**
	 * Add or update roster item.
	 */
	fun addItem(vararg items: RosterItem): IQRequestBuilder<Unit> {
		return context.request.iq {
			type = IQType.Set
			"query"{
				xmlns = XMLNS
				items.forEach {
					addChild(createItem(it))
				}
			}
		}
	}

	fun deleteItem(vararg jids: BareJID): IQRequestBuilder<Unit> {
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
		}
	}

}