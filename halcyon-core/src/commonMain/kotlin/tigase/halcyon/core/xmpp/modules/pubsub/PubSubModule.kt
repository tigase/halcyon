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
package tigase.halcyon.core.xmpp.modules.pubsub

import getFromAttr
import kotlinx.serialization.Serializable
import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.IQRequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.stanzas.wrap

/**
 * Received PubSub item.
 *
 * Event fired when new PubSub event is received.
 *
 * @param pubSubJID JID of PubSub service
 * @param stanza whole received stanza
 * @param nodeName publisher node name
 * @param items list of received items (extracted from `<items>` element)
 */
data class PubSubEventReceivedEvent(
	val pubSubJID: JID?, val stanza: Message, val nodeName: String, val items: List<Element>
) : Event(TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.xmpp.modules.pubsub.PubSubEventReceivedEvent"
	}
}

enum class Affiliation(val xmppName: String) {
	Owner("owner"),
	Publisher("publisher"),
	PublishOnly("publish-only"),
	Member("member"),
	None("none"),
	Outcast("outcast");

	companion object {
		fun byXMPPName(affiliation: String): Affiliation =
			Affiliation.values().firstOrNull { te -> te.xmppName == affiliation }
				?: throw XMPPException(ErrorCondition.BadRequest, "Unknown PubSub Affiliation '$affiliation'")
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
	val node: String, val jid: JID, var state: SubscriptionState, var subid: String? = null
)

class PubSubModule(override val context: Context) : XmppModule {

	private val log = Logger("tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule")

	override val type = TYPE
	override val criteria = Criterion.chain(
		Criterion.name(Message.NAME), Criterion.nameAndXmlns(
			"event", XMLNS_EVENT
		)
	)
	override val features: Array<String>? = null

	companion object {
		const val XMLNS = "http://jabber.org/protocol/pubsub"
		const val TYPE = XMLNS
		const val XMLNS_EVENT = "$XMLNS#event"
		const val XMLNS_OWNER = "$XMLNS#owner"
	}

	override fun initialize() {
	}

	fun create(pubSubJID: JID, node: String, configForm: JabberDataForm? = null): IQRequestBuilder<Unit> {
		val iq = iq {
			type = IQType.Set
			to = pubSubJID
			"pubsub"{
				xmlns = XMLNS
				"create"{
					attribute("node", node)
				}
				configForm?.let {
					"configure"{
						addChild(it.createSubmitForm())
					}
				}
			}
		}
		return context.request.iq(iq)
	}

	/**
	 * Subscribe to a node.
	 *
	 * @param pubSubJID JID of PubSub service.
	 * @param node PubSub node name.
	 */
	fun subscribe(pubSubJID: JID, node: String, jid: JID): IQRequestBuilder<Subscription> {
		val iq = iq {
			type = IQType.Set
			to = pubSubJID
			"pubsub"{
				xmlns = XMLNS
				"subscribe"{
					attribute("node", node)
					attribute("jid", jid.toString())
				}
			}
		}
		return context.request.iq(iq).resultBuilder { element ->
			val s = element.findChild("iq", "pubsub", "subscription") ?: throw XMPPException(ErrorCondition.BadRequest)
			parseSubscriptionElement(s)
		}
	}

	/**
	 * Purge all node items.
	 *
	 * @param pubSubJID JID of PubSub service.
	 * @param node PubSub node name.
	 */
	fun purgeItems(pubSubJID: JID, node: String): IQRequestBuilder<Unit> {
		val iq = iq {
			type = IQType.Set
			to = pubSubJID
			"pubsub"{
				xmlns = XMLNS_OWNER
				"purge"{
					attribute("node", node)
				}
			}
		}

		return context.request.iq(iq)
	}

	/**
	 * Retrieves list of subscriptions.
	 *
	 * @param pubSubJID JID of PubSub service.
	 * @param node PubSub node name.
	 * @return [Request] returning list of [subscriptions][Subscription].
	 */
	fun retrieveSubscriptions(pubSubJID: JID, node: String): IQRequestBuilder<List<Subscription>> {
		val iq = iq {
			type = IQType.Get
			to = pubSubJID
			"pubsub"{
				xmlns = XMLNS_OWNER
				"subscriptions"{
					attribute("node", node)
				}
			}
		}
		return context.request.iq(iq).resultBuilder { element: Element ->
			val subscriptions = element.findChild("iq", "pubsub", "subscriptions") ?: throw XMPPException(
				ErrorCondition.BadRequest
			)
			val nodeName = subscriptions.attributes["node"]
			subscriptions.children.filter { sel -> "subscription" == sel.name }.map { sel ->
				parseSubscriptionElement(sel, nodeName)
			}
		}
	}

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
			nn, JID.parse(jid), SubscriptionState.values().first { state -> state.xmppName == sstate }, subid
		)
	}

	/**
	 * Modify list of subscriptions.
	 *
	 * @param pubSubJID JID of PubSub service.
	 * @param node PubSub node name.
	 * @param subscriptions list of [subscriptions][Subscription] to modify.
	 * @return [Request] with no specific data.
	 */
	fun modifySubscriptions(
		pubSubJID: JID, node: String, subscriptions: List<Subscription>
	): IQRequestBuilder<Unit> {
		val iq = iq {
			type = IQType.Set
			to = pubSubJID
			"pubsub"{
				xmlns = XMLNS_OWNER
				"subscriptions"{
					attribute("node", node)
					subscriptions.forEach { subItem ->
						"subscription"{
							attribute("jid", subItem.jid.toString())
							attribute("subscription", subItem.state.xmppName)
						}
					}
				}
			}
		}
		return context.request.iq(iq)
	}

	override fun process(element: Element) {
		val msg: Message = wrap(element)
		val eventElement = msg.getChildrenNS(
			"event", XMLNS_EVENT
		)!!
		val itemsElement = eventElement.getFirstChild("items")!!
		val nodeName = itemsElement.attributes["node"]!!
		val itemsList = itemsElement.getChildren("item")

		context.eventBus.fire(
			PubSubEventReceivedEvent(
				msg.from, msg, nodeName, itemsList
			)
		)
	}

	data class RetrievedItem(val id: String, val content: Element?)

	data class RetrieveResponse(val jid: JID, val node: String, val items: List<RetrievedItem>)

	fun deleteItem(jid: JID, node: String, itemId: String): IQRequestBuilder<Unit> {
		val iq = iq {
			to = jid
			type = IQType.Set
			"pubsub"{
				xmlns = XMLNS
				"retract"{
					attribute("node", node)
					"item"{
						attribute("id", itemId)
					}
				}
			}
		}
		return context.request.iq(iq)
	}

	fun retrieveItem(jid: JID, node: String, itemId: String? = null): IQRequestBuilder<RetrieveResponse> {
		val iq = iq {
			to = jid
			type = IQType.Get
			"pubsub"{
				xmlns = XMLNS
				"items"{
					attribute("node", node)
					if (itemId != null) {
						"item"{
							attribute("id", itemId)
						}
					}
				}
			}
		}
		return context.request.iq(iq).resultBuilder(this@PubSubModule::buildRetrieveResponse)
	}

	private fun buildRetrieveResponse(iq: Element): RetrieveResponse {
		val items = iq.getChildrenNS("pubsub", XMLNS)!!.getFirstChild("items")!!

		val content = items.children.filter { it.name == "item" }.map { item ->
			RetrievedItem(item.attributes["id"]!!, item.getFirstChild())
		}.toList()

		return RetrieveResponse(iq.getFromAttr()!!, items.attributes["node"]!!, content)
	}

	@Serializable
	data class PublishingInfo(val pubSubJID: JID?, val node: String, val id: String?)

	fun publish(jid: JID?, node: String, itemId: String?, payload: Element? = null): IQRequestBuilder<PublishingInfo> {
		val iq = iq {
			type = IQType.Set
			jid?.let {
				to = it
			}
			"pubsub"{
				xmlns = XMLNS
				"publish"{
					attribute("node", node)
					"item"{
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
		return context.request.iq(iq).resultBuilder { resp ->
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

	data class RetrievedAffiliation(val node: String, val affiliation: Affiliation)

	fun retrieveAffiliations(jid: JID?, node: String? = null): IQRequestBuilder<List<RetrievedAffiliation>> {
		val iq = iq {
			if (jid != null) to = jid
			type = IQType.Get
			"pubsub"{
				xmlns = XMLNS
				"affiliations"{
					if (node != null) attribute("node", node)
				}
			}
		}
		return context.request.iq(iq).resultBuilder { r ->
			r.getChildrenNS("pubsub", XMLNS)?.getFirstChild("affiliations")?.getChildren("affiliation")?.map { a ->
				RetrievedAffiliation(a.attributes["node"]!!, Affiliation.byXMPPName(a.attributes["affiliation"]!!))
			} ?: emptyList()
		}
	}

}

