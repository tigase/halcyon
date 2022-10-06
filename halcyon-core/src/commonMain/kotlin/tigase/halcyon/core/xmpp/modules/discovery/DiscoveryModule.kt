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
package tigase.halcyon.core.xmpp.modules.discovery

import kotlinx.serialization.Serializable
import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.RequestBuilder

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.response
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.stanzas.wrap

class DiscoveryModule(override val context: Context) : XmppModule {

	@Serializable
	data class Identity(val category: String, val type: String, val name: String?)

	@Serializable
	data class Info(
		val jid: JID, val node: String?, val identities: List<Identity>, val features: List<String>,
	)

	@Serializable
	data class Item(val jid: JID, val name: String?, val node: String?)

	@Serializable
	data class Items(val jid: JID, val node: String?, val items: List<Item>)

	companion object {

		const val XMLNS = "http://jabber.org/protocol/disco"
		const val TYPE = XMLNS
		const val XMLNS_INFO = "$XMLNS#info"
		const val XMLNS_ITEMS = "$XMLNS#items"
	}

	override val type: String = TYPE
	override val criteria: Criteria =
		Criterion.or(Criterion.chain(Criterion.name("iq"), Criterion.nameAndXmlns("query", XMLNS_INFO)),
					 Criterion.chain(Criterion.name("iq"), Criterion.nameAndXmlns("query", XMLNS_ITEMS)))
	override val features: Array<String> = arrayOf(XMLNS_INFO, XMLNS_ITEMS)

	var clientName = "Halcyon Based Client"
	var clientVersion = "1.0.0"
	var clientCategory = "client"
	var clientType = "bot"

	private val detailsProviders = mutableListOf<NodeDetailsProvider>()

	inner class DefaultNodeDetailsProvider : NodeDetailsProvider {

		override fun getIdentities(sender: BareJID?, node: String?): List<Identity> =
			if (node == null) listOf(getClientIdentity()) else emptyList()

		override fun getFeatures(sender: BareJID?, node: String?): List<String> =
			if (node == null) context.modules.getAvailableFeatures().toList() else emptyList()

		override fun getItems(sender: BareJID?, node: String?): List<Item> = emptyList()

	}

	override fun initialize() {
		addNodeDetailsProvider(DefaultNodeDetailsProvider())
	}

	fun addNodeDetailsProvider(provider: NodeDetailsProvider) {
		this.detailsProviders.add(provider)
	}

	override fun process(element: Element) {
		val iq = wrap<IQ>(element)
		val queryXmlns = iq.getFirstChild("query")?.xmlns
		when {
			iq.type == IQType.Get && queryXmlns == XMLNS_ITEMS -> processGetItems(iq)
			iq.type == IQType.Get && queryXmlns == XMLNS_INFO -> processGetInfo(iq)
			iq.type != IQType.Get -> throw XMPPException(ErrorCondition.NotAllowed)
			else -> throw XMPPException(ErrorCondition.FeatureNotImplemented)
		}
	}

	private fun processGetInfo(iq: IQ) {
		val node = iq.getChildrenNS("query", XMLNS_INFO)?.attributes?.get("node")
		val identities = mutableListOf<Identity>()
		val features = mutableListOf<String>()

		detailsProviders.forEach { provider ->
			features.addAll(provider.getFeatures(iq.from?.bareJID, node))
			identities.addAll(provider.getIdentities(iq.from?.bareJID, node))
		}

		if (identities.isEmpty() || features.isEmpty()) throw XMPPException(ErrorCondition.ItemNotFound)

		context.writer.writeDirectly(response(iq) {
			"query"{
				xmlns = XMLNS_INFO
				node?.let {
					attribute("node", it)
				}
				identities.forEach { identity ->
					"identity"{
						attribute("category", identity.category)
						attribute("type", identity.type)
						identity.name?.let {
							attribute("name", it)
						}
					}
				}

				features.forEach { feature ->
					"feature"{
						attribute("var", feature)
					}
				}
			}
		})
	}

	private fun processGetItems(iq: IQ) {
		val node = iq.getChildrenNS("query", XMLNS_ITEMS)?.attributes?.get("node")
		val items = detailsProviders.map { it.getItems(iq.from?.bareJID, node) }.flatten()

//		if (node == CommandsModule.NODE) {
//			val module = context.modules.getModuleOrNull<CommandsModule>(CommandsModule.TYPE) ?: throw XMPPException(
//				ErrorCondition.ItemNotFound
//			)
//			items.addAll(module.getDiscoItems(iq.from!!.bareJID))
//		}

		context.writer.writeDirectly(response(iq) {
			"query"{
				xmlns = XMLNS_ITEMS
				node?.let {
					attribute("node", it)
				}

				items.forEach { item ->
					"item"{
						attribute("jid", item.jid.toString())
						item.node?.let { attribute("node", it) }
						item.name?.let { attribute("name", it) }
					}
				}
			}
		})
	}

	/**
	 * Prepares request for disco#info.
	 * @param jid JID of entity to ask for info (optional).
	 * @param node name of node to ask for (optional).
	 * @return request returns [Info] in case of success.
	 */
	fun info(jid: JID?, node: String? = null): RequestBuilder<Info, IQ> {
		val stanza = iq {
			type = IQType.Get
			if (jid != null) to = jid
			"query"{
				xmlns = XMLNS_INFO
				node?.let {
					attribute("node", it)
				}
			}
		}
		return context.request.iq(stanza).map(this@DiscoveryModule::buildInfo)
	}

	private fun buildInfo(response: Element): Info {
		val query = response.getChildrenNS("query", XMLNS_INFO)!!
		val node = query.attributes["node"]
		val jid = JID.parse(response.attributes["from"]!!)

		val identities = query.getChildren("identity").map {
			Identity(it.attributes["category"]!!, it.attributes["type"]!!, it.attributes["name"])
		}.toList()
		val features = query.getChildren("feature").map {
			it.attributes["var"]!!
		}.toList()

		return Info(jid, node, identities, features)
	}

	fun findComponent(predicate: (Info) -> Boolean, consumer: (Info) -> Unit) {
		val domain = context.boundJID?.bareJID?.domain!!
		var found = false
		items(domain.toJID()).response {
			if (it.isSuccess) {
				val items = it.getOrThrow()
				items.items.forEach {
					info(it.jid).response {
						if (it.isSuccess) {
							val inf = it.getOrThrow()
							if (!found && predicate.invoke(inf)) {
								found = true
								consumer.invoke(inf)
							}
						}
					}.send()
				}
			}
		}.send()
	}

	fun items(jid: JID?, node: String? = null): RequestBuilder<Items, IQ> {
		val stanza = iq {
			type = IQType.Get
			if (jid != null) to = jid
			"query"{
				xmlns = XMLNS_ITEMS
				node?.let {
					attribute("node", it)
				}
			}
		}
		return context.request.iq(stanza).map(this@DiscoveryModule::buildItems)
	}

	private fun buildItems(response: Element): Items {
		val query = response.getChildrenNS("query", XMLNS_ITEMS)!!
		val node = query.attributes["node"]
		val jid = JID.parse(response.attributes["from"]!!)

		val items = query.getChildren("item").map {
			Item(JID.parse(it.attributes["jid"]!!), it.attributes["name"], it.attributes["node"])
		}.toList()

		return Items(jid, node, items)
	}

	fun getClientIdentity(): Identity = Identity(clientCategory, clientType, "$clientName $clientVersion")

	internal fun discoverAccountFeatures() {
		val ownJid = context.boundJID?.bareJID ?: return
		info(JID.parse(ownJid.toString())).response {
			if (it.isSuccess) {
				it.getOrNull()?.let { info ->
					context.eventBus.fire(AccountFeaturesReceivedEvent(info.identities, info.features))
				}
			}
		}.send()
	}

	internal fun discoverServerFeatures() {
		val caps = context.modules.getModuleOrNull<EntityCapabilitiesModule>(EntityCapabilitiesModule.TYPE)
			?.getServerCapabilities()
		if (caps != null) {
			context.eventBus.fire(ServerFeaturesReceivedEvent(caps.identities, caps.features))
		} else {
			val ownJid = context.boundJID?.bareJID ?: return
			info(JID.parse(ownJid.domain)).response {
				if (it.isSuccess) {
					it.getOrNull()?.let { info ->
						context.eventBus.fire(ServerFeaturesReceivedEvent(info.identities, info.features))
					}
				}
			}.send()
		}
	}
}

data class AccountFeaturesReceivedEvent(val identities: List<DiscoveryModule.Identity>, val features: List<String>) :
	Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.discovery.AccountFeaturesReceivedEvent"
	}
}

data class ServerFeaturesReceivedEvent(val identities: List<DiscoveryModule.Identity>, val features: List<String>) :
	Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.discovery.ServerFeaturesReceivedEvent"
	}
}