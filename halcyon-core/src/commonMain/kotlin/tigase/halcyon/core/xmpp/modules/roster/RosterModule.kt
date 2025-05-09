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
package tigase.halcyon.core.xmpp.modules.roster

import kotlinx.serialization.Serializable
import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.AbstractXmppIQModule
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq

/**
 * Base event for roster  item manipulation events.
 */
sealed class RosterEvent(
    /** received XML element describing roster item. Should be used only handle extensions. */
    @Suppress("unused")
    val itemElement: Element,
    /** Parsed roster item. */
    val item: RosterItem
) : Event(TYPE) {

    companion object : EventDefinition<RosterEvent> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.roster.RosterEvent"
    }

    /**
     * Fired when new item is added to roster.
     */
    class ItemAdded(element: Element, item: RosterItem) : RosterEvent(element, item)

    /**
     * Fired when roster item is modified.
     */
    class ItemUpdated(
        element: Element,
        /** Roster item before modification. */
        @Suppress("unused")
        val oldItem: RosterItem,
        item: RosterItem
    ) : RosterEvent(element, item)

    /**
     * Fired when item is removed from roster.
     */
    class ItemRemoved(element: Element, item: RosterItem) : RosterEvent(element, item)
}

/**
 * Event fired when roster is loaded from XMPP server.
 */
class RosterLoadedEvent : Event(TYPE) {

    companion object : EventDefinition<RosterLoadedEvent> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.roster.RosterLoadedEvent"
    }
}

/**
 * Event fired when local roster modification processing has been completed.
 */
class RosterUpdatedEvent : Event(TYPE) {

    companion object : EventDefinition<RosterUpdatedEvent> {

        override val TYPE = "tigase.halcyon.core.xmpp.modules.roster.RosterUpdatedEvent"
    }
}

/**
 * Subscription state. Check [documentation](https://www.rfc-editor.org/rfc/rfc6121#section-2.1.2.5) for details.
 */
enum class Subscription(val value: String) {

    /**
     * The user and the contact have subscriptions to each other's presence (also called a "mutual subscription").
     */
    Both("both"),

    /**
     * The contact has a subscription to the user's presence,
     * but the user does not have a subscription to the contact's presence
     */
    From("from"),

    /**
     * The user does not have a subscription to the contact's presence,
     * and the contact does not have a subscription to the user's presence; this is the default value,
     * so if the subscription attribute is not included then the state is to be understood as "none".
     */
    None("none"),

    /**
     * Item must be removed from local roster.
     */
    Remove("remove"),

    /**
     * The user has a subscription to the contact's presence, but the
     * contact does not have a subscription to the user's presence.
     */
    To("to")
}

/**
 * Subscription pending indicator.
 */
enum class Ask(val value: String) {

    /**
     * Subscription pending is progress.
     */
    Subscribe("subscribe")
}

interface RosterItemAnnotationProcessor {

    fun prepareRosterGetRequest(stanza: IQ)

    fun processRosterItem(item: Element): RosterItemAnnotation?
}

interface RosterItemAnnotation

/**
 * Represents roster item.
 */
@Serializable
data class RosterItem(
    /** Identifier of roster item. */
    val jid: BareJID,
    /** Human-readable name of item. */
    val name: String?,
    /** List of groups item belongs to. */
    val groups: List<String> = emptyList(),
    /** Indicates that subscription request is processed.  */
    val ask: Ask? = null,
    /** Subscription state. */
    val subscription: Subscription? = null,
    /** Is subscription pre-approved. */
    val approved: Boolean = false,
    /** List of optional annotations of roster item. */
    val annotations: Array<RosterItemAnnotation> = emptyArray()
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RosterItem) return false

        if (jid != other.jid) return false
        if (name != other.name) return false
        if (groups != other.groups) return false
        if (ask != other.ask) return false
        if (subscription != other.subscription) return false
        if (approved != other.approved) return false
        if (!annotations.contentEquals(other.annotations)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = jid.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + groups.hashCode()
        result = 31 * result + (ask?.hashCode() ?: 0)
        result = 31 * result + (subscription?.hashCode() ?: 0)
        result = 31 * result + approved.hashCode()
        result = 31 * result + annotations.contentHashCode()
        return result
    }
}

/**
 * Response for roster-request.
 */
data class RosterResponse(
    /** Version of roster. */
    val version: String?
)

/**
 * Configuration of [RosterModule].
 */
@HalcyonConfigDsl
interface RosterModuleConfiguration {

    /**
     * Specify a store to keep received roster.
     */
    var store: RosterStore
}

/**
 * Module for managing roster.
 */
class RosterModule(context: Context) :
    AbstractXmppIQModule(
        context,
        TYPE,
        emptyArray(),
        Criterion.chain(
            Criterion.name(IQ.NAME),
            Criterion.xmlns(XMLNS)
        )
    ),
    RosterModuleConfiguration {

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.roster.RosterModule")

    companion object : XmppModuleProvider<RosterModule, RosterModuleConfiguration> {

        const val XMLNS = "jabber:iq:roster"
        override val TYPE = XMLNS
        override fun instance(context: Context): RosterModule = RosterModule(context)

        override fun configure(module: RosterModule, cfg: RosterModuleConfiguration.() -> Unit) =
            module.cfg()
    }

    override var store: RosterStore = InMemoryRosterStore()

    /**
     * Prepares request for roster.
     */
    fun rosterGet(): RequestBuilder<RosterResponse, IQ> {
        val iq = iq {
            type = IQType.Get
            "query" {
                xmlns = XMLNS
                attribute("ver", store.getVersion() ?: "")
            }
        }
        updateRequest(iq)
        return context.request.iq(iq).map {
            val result =
                it.getChildrenNS("query", XMLNS)?.let(this@RosterModule::processQueryResponse)
                    ?: RosterResponse(
                        null
                    )
            context.eventBus.fire(RosterLoadedEvent())
            context.eventBus.fire(RosterUpdatedEvent())
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
// 		rosterItem.ask?.let { attribute("ask", it.value) }
// 		rosterItem.subscription?.let { attribute("subscription", it.value) }
// 		rosterItem.approved?.let {
// 			attribute(
// 				"approved", if (it) {
// 					"1"
// 				} else {
// 					"0"
// 				}
// 			)
// 		}
        rosterItem.groups.forEach { groupName ->
            "group" {
                +groupName
            }
        }
    }

    private fun processQueryResponse(query: Element): RosterResponse {
        log.fine { "Processing roster data" }
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
            log.fine { "Remove item ${item.jid}" }
            store.removeItem(item.jid)
            context.eventBus.fire(RosterEvent.ItemRemoved(itemElement, item))
        } else if (oldItem == null && item.subscription != Subscription.Remove) {
            log.fine { "Add item ${item.jid}" }
            store.addItem(item)
            context.eventBus.fire(RosterEvent.ItemAdded(itemElement, item))
        } else if (oldItem != null && item.subscription != Subscription.Remove) {
            log.fine { "Update item ${item.jid}" }
            store.updateItem(item)
            context.eventBus.fire(RosterEvent.ItemUpdated(itemElement, oldItem, item))
        } else {
            log.fine { "Ignore item ${item.jid}" }
        }
    }

    private fun parseItem(item: Element): RosterItem {
        val jid = item.attributes["jid"]?.toBareJID() ?: throw XMPPException(
            ErrorCondition.BadRequest,
            "Missing JID in roster item."
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
            null -> false
            else -> throw XMPPException(
                ErrorCondition.BadRequest,
                "Unknown value of approved field."
            )
        }
        val groups = item.getChildren("group").map { it.value ?: "" }

        val annotations = createAnnotations(item)

        return RosterItem(jid, name, groups, ask, subscription, approved, annotations)
    }

    private fun createAnnotations(item: Element): Array<RosterItemAnnotation> =
        context.modules.getModules().filter {
            it is RosterItemAnnotationProcessor
        }
            .mapNotNull {
                (it as RosterItemAnnotationProcessor).processRosterItem(item)
            }.toTypedArray()

    override fun processGet(element: IQ) = throw XMPPException(ErrorCondition.NotAllowed)

    override fun processSet(element: IQ) {
        val boundJID =
            context.boundJID
                ?: throw HalcyonException("Session is not bound. Cannot process roster request.")
        val from = element.from
        if (from != null && from.bareJID != boundJID.bareJID) {
            throw XMPPException(ErrorCondition.NotAllowed)
        }
        element.getChildrenNS("query", XMLNS)?.let(this@RosterModule::processQueryResponse)
        context.eventBus.fire(RosterUpdatedEvent())
    }

    /**
     * Prepares request to add or update the roster item.
     * @param items [RosterItem]s to add or update.
     */
    @Suppress("unused")
    fun addItem(vararg items: RosterItem): RequestBuilder<Unit, IQ> = context.request.iq {
        type = IQType.Set
        "query" {
            xmlns = XMLNS
            items.forEach {
                addChild(createItem(it))
            }
        }
    }.map {}

    /**
     * Prepares request to remove items identifed by given JIDs from roster.
     * @param jids ) identifiers of items to remove.
     */
    @Suppress("unused")
    fun deleteItem(vararg jids: BareJID): RequestBuilder<Unit, IQ> = context.request.iq {
        type = IQType.Set
        "query" {
            xmlns = XMLNS
            jids.forEach { jid ->
                "item" {
                    attribute("jid", jid.toString())
                    attribute("subscription", "remove")
                }
            }
        }
    }.map {}

    /**
     * Returns all items from roster.
     */
    @Suppress("unused")
    fun getAllItems(): List<RosterItem> = store.getAllItems()
}
