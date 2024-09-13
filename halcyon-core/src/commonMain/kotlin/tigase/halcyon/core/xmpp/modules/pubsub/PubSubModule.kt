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
package tigase.halcyon.core.xmpp.modules.pubsub

import kotlinx.serialization.Serializable
import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.requests.XMPPError
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.stanzas.*

/**
 * Received PubSub item.
 *
 * Event fired when new PubSub event is received.
 *
 * @param pubSubJID JID of PubSub service
 * @param stanza whole received stanza
 * @param nodeName publisher node name
 */
sealed class PubSubItemEvent(
	val pubSubJID: JID?, val stanza: Message, val nodeName: String,
) : Event(TYPE) {

	class Published(
		pubSubJID: JID?, stanza: Message, nodeName: String, val itemId: String?, val content: Element?,
	) : PubSubItemEvent(pubSubJID, stanza, nodeName)

	class Retracted(
		pubSubJID: JID?, stanza: Message, nodeName: String, val itemId: String?,
	) : PubSubItemEvent(pubSubJID, stanza, nodeName)

	companion object : EventDefinition<PubSubItemEvent> {

		override val TYPE = "tigase.halcyon.core.xmpp.modules.pubsub.PubSubEventReceivedEvent"
	}
}

/**
 * Affiliations.
 */
enum class Affiliation(val xmppName: String) {

	Owner("owner"), Publisher("publisher"), PublishOnly("publish-only"), Member("member"), None("none"), Outcast("outcast");

	companion object {

		fun byXMPPName(affiliation: String): Affiliation =
			values().firstOrNull { te -> te.xmppName == affiliation } ?: throw XMPPException(
				ErrorCondition.BadRequest, "Unknown PubSub Affiliation '$affiliation'"
			)
	}

}

/**
 * States of subscription.
 */
enum class SubscriptionState(val xmppName: String) {

	/**
	 * The node MUST NOT send event notifications or payloads to the Entity.
	 */
	None("none"),

	/**
	 * An entity has requested to subscribe to a node and the request has not yet been approved by a node owner.
	 * The node MUST NOT send event notifications or payloads to the entity while it is in this state.
	 */
	Pending("pending"),

	/**
	 * An entity has subscribed but its subscription options have not yet been configured.
	 * The node MAY send event notifications or payloads to the entity while it is in this state.
	 * The service MAY timeout unconfigured subscriptions.
	 */
	Unconfigured("unconfigured"),

	/**
	 * An entity is subscribed to a node.The node MUST send all event notifications
	 * (and, if configured, payloads) to the entity while it is in this state
	 * (subject to subscriber configuration and content filtering).
	 */
	Subscribed("subscribed")
}

/**
 * Subscription details.
 *
 * @param node node name
 * @param jid jid of subscriber
 * @param state [state][SubscriptionState] of subscription
 * @param subid subscription identifier
 */
data class Subscription(
	val node: String, val jid: JID, var state: SubscriptionState, var subid: String? = null,
)

/**
 * PubSub Module.
 */

@HalcyonConfigDsl
interface PubSubModuleConfig
class PubSubModule(override val context: Context) : XmppModule, PubSubModuleConfig {

//	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule")

	override val type = TYPE
	override val criteria = Criterion.chain(Criterion.name(Message.NAME), Criterion.nameAndXmlns("event", XMLNS_EVENT))
	override val features: Array<String>? = null

	companion object : XmppModuleProvider<PubSubModule, PubSubModuleConfig> {

		const val XMLNS = "http://jabber.org/protocol/pubsub"
		override val TYPE = XMLNS
		const val XMLNS_EVENT = "$XMLNS#event"
		const val XMLNS_OWNER = "$XMLNS#owner"

		override fun instance(context: Context): PubSubModule = PubSubModule(context)
		override fun configure(module: PubSubModule, cfg: PubSubModuleConfig.() -> Unit) = module.cfg()
	}

	/**
	 * Prepares PubSub node creation request.
	 *
	 * @param pubSubJID JabberID of PubSub service
	 * @param node node name
	 * @param configForm form with configuration
	 */
	fun create(
		pubSubJID: JID, node: String, configForm: JabberDataForm? = null,
	): RequestBuilder<Unit, IQ> {
		val iq = iq {
			type = IQType.Set
			to = pubSubJID
			"pubsub" {
				xmlns = XMLNS
				"create" {
					attribute("node", node)
				}
				configForm?.let {
					"configure" {
						addChild(it.createSubmitForm())
					}
				}
			}
		}
		return context.request.iq(iq).map {}
	}

	/**
	 * Prepares Subscribe to a node request.
	 *
	 * @param pubSubJID JID of PubSub service.
	 * @param node PubSub node name.
	 * @return In case of success returns [Subscription] object.
	 */
	@Suppress("unused")
	fun subscribe(pubSubJID: JID, node: String, jid: JID): RequestBuilder<Subscription, IQ> {
		val iq = iq {
			type = IQType.Set
			to = pubSubJID
			"pubsub" {
				xmlns = XMLNS
				"subscribe" {
					attribute("node", node)
					attribute("jid", jid.toString())
				}
			}
		}
		return context.request.iq(iq).map { element ->
			val s = element.findChild("iq", "pubsub", "subscription") ?: throw XMPPException(ErrorCondition.BadRequest)
			parseSubscriptionElement(s)
		}
	}

	@Suppress("unused")
	fun unsubscribe(pubSubJID: JID, node: String, jid: JID): RequestBuilder<Subscription, IQ> {
		val iq = iq {
			type = IQType.Set
			to = pubSubJID
			"pubsub" {
				xmlns = XMLNS
				"unsubscribe" {
					attribute("node", node)
					attribute("jid", jid.toString())
				}
			}
		}
		return context.request.iq(iq).map { element ->
			val s = element.findChild("iq", "pubsub", "subscription") ?: throw XMPPException(ErrorCondition.BadRequest)
			parseSubscriptionElement(s)
		}
	}

	/**
	 * Prepares purge all node items request.
	 *
	 * @param pubSubJID JID of PubSub service.
	 * @param node PubSub node name.
	 */
	fun purgeItems(pubSubJID: JID, node: String): RequestBuilder<Unit, IQ> {
		val iq = iq {
			type = IQType.Set
			to = pubSubJID
			"pubsub" {
				xmlns = XMLNS_OWNER
				"purge" {
					attribute("node", node)
				}
			}
		}

		return context.request.iq(iq).map {}
	}

	private fun retrieveSubscriptions(
		requestXMLNS: String, pubSubJID: JID, node: String,
	): RequestBuilder<List<Subscription>, IQ> {
		val iq = iq {
			type = IQType.Get
			to = pubSubJID
			"pubsub" {
				xmlns = requestXMLNS
				"subscriptions" {
					attribute("node", node)
				}
			}
		}
		return context.request.iq(iq).map { element: Element ->
			val subscriptions =
				element.findChild("iq", "pubsub", "subscriptions") ?: throw XMPPException(ErrorCondition.BadRequest)
			val nodeName = subscriptions.attributes["node"]
			subscriptions.children.filter { sel -> "subscription" == sel.name }.map { sel ->
				parseSubscriptionElement(sel, nodeName)
			}
		}
	}

	/**
	 * Prepares retrieves list of subscriptions request.
	 *
	 * @param pubSubJID JID of PubSub service.
	 * @param node PubSub node name.
	 */
	fun retrieveSubscriptions(pubSubJID: JID, node: String): RequestBuilder<List<Subscription>, IQ> =
		retrieveSubscriptions(XMLNS, pubSubJID, node)

	/**
	 * Prepares retrieves list of subscriptions request as node owner.
	 *
	 * @param pubSubJID JID of PubSub service.
	 * @param node PubSub node name.
	 */
	fun retrieveSubscriptionsAsOwner(
		pubSubJID: JID, node: String,
	): RequestBuilder<List<Subscription>, IQ> = retrieveSubscriptions(XMLNS_OWNER, pubSubJID, node)

	private fun parseSubscriptionElement(element: Element, nodeName: String? = null): Subscription {
		val jid = element.attributes["jid"] ?: throw XMPPException(ErrorCondition.BadRequest, "No JID")
		val sstate = element.attributes["subscription"] ?: throw XMPPException(
			ErrorCondition.BadRequest, "No subscription state"
		)
		val subid = element.attributes["subid"]

		val nn = element.attributes["node"] ?: nodeName ?: throw XMPPException(
			ErrorCondition.BadRequest, "Unknown node name"
		)

		return Subscription(
			nn, jid.toJID(), SubscriptionState.values().first { state -> state.xmppName == sstate }, subid
		)
	}

	/**
	 * Prepare modify list of subscriptions request.
	 *
	 * @param pubSubJID JID of PubSub service.
	 * @param node PubSub node name.
	 * @param subscriptions list of [subscriptions][Subscription] to modify.
	 */
	fun modifySubscriptions(
		pubSubJID: JID, node: String, subscriptions: List<Subscription>,
	): RequestBuilder<Unit, IQ> {
		val iq = iq {
			type = IQType.Set
			to = pubSubJID
			"pubsub" {
				xmlns = XMLNS_OWNER
				"subscriptions" {
					attribute("node", node)
					subscriptions.forEach { subItem ->
						"subscription" {
							attribute("jid", subItem.jid.toString())
							attribute("subscription", subItem.state.xmppName)
						}
					}
				}
			}
		}
		return context.request.iq(iq).map {}
	}

	override fun process(element: Element) {
		val msg: Message = wrap(element)
		val eventElement = msg.getChildrenNS("event", XMLNS_EVENT)!!
		val itemsElement = eventElement.getFirstChild("items")!!
		val nodeName = itemsElement.attributes["node"]!!

		itemsElement.children.forEach { item ->
			when (item.name) {
				"retract" -> {
					val id = item.attributes["id"]
					context.eventBus.fire(PubSubItemEvent.Retracted(msg.from, msg, nodeName, id))
				}

				"item" -> {
					val id = item.attributes["id"]
					context.eventBus.fire(PubSubItemEvent.Published(msg.from, msg, nodeName, id, item.getFirstChild()))
				}

				else -> throw XMPPException(ErrorCondition.BadRequest)
			}
		}

	}

	/**
	 * Published PubSub item.
	 *
	 * @param id id of published item
	 * @param content published content.
	 */
	data class RetrievedItem(val id: String, val content: Element?)

	/**
	 * Response for retrieve item(s).
	 *
	 * @param jid JabberID of PubSub service
	 * @param node node name
	 * @param items list of [RetrievedItem]s.
	 */
	data class RetrieveResponse(val jid: JID, val node: String, val items: List<RetrievedItem>)

	/**
	 * Prepare delete item request.
	 *
	 * @param jid JID of PubSub service.
	 * @param node PubSub node name.
	 * @param itemId ID of published item.
	 */
	fun deleteItem(jid: JID?, node: String, itemId: String): RequestBuilder<Unit, IQ> {
		val iq = iq {
			if (jid != null) to = jid
			type = IQType.Set
			"pubsub" {
				xmlns = XMLNS
				"retract" {
					attribute("node", node)
					"item" {
						attribute("id", itemId)
					}
				}
			}
		}
		return context.request.iq(iq).map {}
	}

	fun deleteNode(jid: JID?, node: String): RequestBuilder<Unit, IQ> {
		val iq = iq {
			if (jid != null) to = jid
			type = IQType.Set
			"pubsub" {
				xmlns = XMLNS_OWNER
				"delete" {
					attribute("node", node)
				}
			}
		}
		return context.request.iq(iq).map {}
	}

	/**
	 * Prepare [retrieve item](https://xmpp.org/extensions/xep-0060.html#subscriber-retrieve) request.
	 *
	 * @param jid JID of PubSub service.
	 * @param node PubSub node name.
	 * @param itemId ID of published item. If not provided, then all published items will be returned.
	 *
	 * @return [RetrieveResponse] what contains list of published items.
	 */
	fun retrieveItem(
		jid: JID?, node: String, itemId: String? = null, maxItems: Int? = null
	): RequestBuilder<RetrieveResponse, IQ> {
		val iq = iq {
			if (jid != null) to = jid
			type = IQType.Get
			"pubsub" {
				xmlns = XMLNS
				"items" {
					attribute("node", node)
					if (maxItems != null) {
						attribute("max_items", maxItems.toString());
					}
					if (itemId != null) {
						"item" {
							attribute("id", itemId)
						}
					}
				}
			}
		}
		return context.request.iq(iq).map { resp: IQ -> buildRetrieveResponse(resp, node, itemId) }
	}

	private fun buildRetrieveResponse(iq: IQ, node: String, requestedItemId: String?): RetrieveResponse {
		val items = iq.getChildrenNS("pubsub", XMLNS)!!.getFirstChild("items")!!

		val content = items.children.filter { it.name == "item" }.map { item ->
			RetrievedItem(item.attributes["id"]!!, item.getFirstChild())
		}.toList()

		if (requestedItemId != null && content.isEmpty()) throw XMPPError(
			iq, ErrorCondition.ItemNotFound, "There is no item $requestedItemId in node $node."
		)

		return RetrieveResponse(iq.getFromAttr()!!, items.attributes["node"]!!, content)
	}

	/**
	 * Response for publish request.
	 * @param pubSubJID JID of PubSub service.
	 * @param node PubSub node name.
	 * @param id ID of published item (may be different from requested ID).
	 */
	@Serializable
	data class PublishingInfo(val pubSubJID: JID?, val node: String, val id: String?)

	/**
	 * Prepare [publish item](https://xmpp.org/extensions/xep-0060.html#publisher-publish) request.
	 *
	 * @param jid JID of PubSub service.
	 * @param node PubSub node name.
	 * @param itemId ID of publishing item.
	 * @param payload to publish.
	 *
	 * @return [PublishingInfo]
	 */
	fun publish(
		jid: JID?, node: String, itemId: String?, payload: Element? = null,
	): RequestBuilder<PublishingInfo, IQ> {
		val iq = iq {
			type = IQType.Set
			jid?.let {
				to = it
			}
			"pubsub" {
				xmlns = XMLNS
				"publish" {
					attribute("node", node)
					"item" {
						if (itemId != null) {
							attributes["id"] = itemId
						}
						payload?.let {
							addChild(it)
						}
					}
				}
			}
		}
		return context.request.iq(iq).map { resp ->
			val publish = resp.getChildrenNS("pubsub", XMLNS)?.getFirstChild("publish")
				?: throw HalcyonException("No publish element")
			val item = publish.getFirstChild("item") ?: throw HalcyonException("No item element")
			val j = resp.getFromAttr() ?: throw HalcyonException("No sender JID")
			PublishingInfo(
				j,
				publish.attributes["node"] ?: throw HalcyonException("No node name"),
				item.attributes["id"] ?: throw HalcyonException("No item ID")
			)
		}
	}

	/**
	 * Retrieved affiliation.
	 * @param node name of node
	 * @param affiliation affiliation
	 */
	data class RetrievedAffiliation(val node: String, val affiliation: Affiliation)

	/**
	 * Prepare [retrieve affiliation](https://xmpp.org/extensions/xep-0060.html#entity-affiliations) request.
	 * @param jid JID of PubSub service.
	 * @param node PubSub node name.
	 * @return list of [RetrievedAffiliation]
	 */
	fun retrieveAffiliations(
		jid: JID?, node: String? = null,
	): RequestBuilder<List<RetrievedAffiliation>, IQ> {
		val iq = iq {
			if (jid != null) to = jid
			type = IQType.Get
			"pubsub" {
				xmlns = XMLNS
				"affiliations" {
					if (node != null) attribute("node", node)
				}
			}
		}
		return context.request.iq(iq).map { r ->
			r.getChildrenNS("pubsub", XMLNS)?.getFirstChild("affiliations")?.getChildren("affiliation")?.map { a ->
				RetrievedAffiliation(
					a.attributes["node"]!!, Affiliation.byXMPPName(a.attributes["affiliation"]!!)
				)
			} ?: emptyList()
		}
	}

}

fun Element.isPubSubMessage(): Boolean {
	if (this.name != Message.NAME) return false
	return this.getChildrenNS("event", PubSubModule.XMLNS_EVENT) != null
}
