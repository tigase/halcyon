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
package tigase.halcyon.core.xmpp.modules.caps

import korlibs.crypto.sha1
import kotlinx.serialization.Serializable
import tigase.halcyon.core.*
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.*
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.modules.StreamFeaturesModule
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.modules.discovery.NodeDetailsProvider
import tigase.halcyon.core.xmpp.stanzas.Presence
import tigase.halcyon.core.xmpp.stanzas.wrap

/**
 * Configuration of [EntityCapabilitiesModule].
 */
@HalcyonConfigDsl
interface EntityCapabilitiesModuleConfig {

	/**
	 * Client node name.
	 */
	var node: String

	/**
	 * Specify a cache to keep discovered entity capabilities.
	 */
	var cache: EntityCapabilitiesCache

	/**
	 * Allows to store capabilities with invalid verification string. `false` by default.
	 * Check [Security Considerations¶](https://xmpp.org/extensions/xep-0115.html#security) chapter for details.
	 */
	var storeInvalid: Boolean

}

/**
 * This module implements [XEP-0115: XMPP Ping](https://xmpp.org/extensions/xep-0115.html).
 */
class EntityCapabilitiesModule(
	override val context: Context,
	private val discoModule: DiscoveryModule,
	private val streamFeaturesModule: StreamFeaturesModule,
) : XmppModule, StanzaInterceptor, EntityCapabilitiesModuleConfig {

	/**
	 * Represents entity capabilities for specific node.
	 */
	@Serializable
	data class Caps(
		/** Node name. */
		val node: String,
		/** List of node identities. */
		val identities: List<DiscoveryModule.Identity>,
		/** List of featues provided by node. */
		val features: List<String>,
	)

	companion object : XmppModuleProvider<EntityCapabilitiesModule, EntityCapabilitiesModuleConfig> {

		const val XMLNS = "http://jabber.org/protocol/caps"
		override val TYPE = XMLNS
		override fun instance(context: Context): EntityCapabilitiesModule = EntityCapabilitiesModule(
			context,
			discoModule = context.modules.getModule(DiscoveryModule),
			streamFeaturesModule = context.modules.getModule(StreamFeaturesModule)
		)

		override fun configure(module: EntityCapabilitiesModule, cfg: EntityCapabilitiesModuleConfig.() -> Unit) =
			module.cfg()

		override fun requiredModules() = listOf(DiscoveryModule, StreamFeaturesModule)

		override fun doAfterRegistration(module: EntityCapabilitiesModule, moduleManager: ModulesManager) {
			module.initialize()
			moduleManager.registerInterceptors(arrayOf(module))
		}

	}

	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule")

	override val type: String = TYPE
	override val criteria: Criteria? = null
	override val features: Array<String> = arrayOf(XMLNS)

	override var storeInvalid: Boolean = false
	override var node: String = "https://tigase.org/halcyon"
	override var cache: EntityCapabilitiesCache = DefaultEntityCapabilitiesCache()

	private var verificationStringCache: String? by propertySimple(Scope.Session, null)

	inner class CapsNodeDetailsProvider : NodeDetailsProvider {

		override fun getIdentities(sender: BareJID?, node: String?): List<DiscoveryModule.Identity> {
			val ver = getVerificationString()
			return if (node == "${this@EntityCapabilitiesModule.node}#$ver") {
				listOf(discoModule.getClientIdentity())
			} else {
				emptyList()
			}
		}

		override fun getFeatures(sender: BareJID?, node: String?): List<String> {
			val ver = getVerificationString()
			return if (node == "${this@EntityCapabilitiesModule.node}#$ver") context.modules.getAvailableFeatures()
				.toList() else emptyList()
		}

		override fun getItems(sender: BareJID?, node: String?): List<DiscoveryModule.Item> = emptyList()

	}

	private fun initialize() {
		this.discoModule.addNodeDetailsProvider(CapsNodeDetailsProvider())
		context.eventBus.register(HalcyonStateChangeEvent) {
			if (it.newState == AbstractHalcyon.State.Connected) {
				checkServerFeatures()
			}
		}
	}

	private fun getVerificationString(): String {
		if (verificationStringCache == null) {
			val clientFeatures = context.modules.getAvailableFeatures().toList()
			val clientIdentities = listOf(discoModule.getClientIdentity())
			verificationStringCache = calculateVer(clientIdentities, clientFeatures)
		}
		return verificationStringCache!!
	}

	private fun checkServerFeatures() {
		getServerNode()?.let { node ->
			val jid = context.boundJID ?: return
			if (cache.isCached(node)) return
			discoModule.info(jid.domain.toBareJID(), node).response {
				if (it.isSuccess) storeInfo(node, it.getOrThrow())
			}.send()
		}
	}

	private fun getServerNode(): String? {
		return streamFeaturesModule.streamFeatures?.getChildrenNS("c", XMLNS)?.let { c ->
			val node = c.attributes["node"]
			val ver = c.attributes["ver"]
			"$node#$ver"
		}
	}

	override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

	private fun processIncomingPresence(stanza: Element) {
		val presence = wrap<Presence>(stanza)
		val c: Element = presence.getChildrenNS("c", XMLNS) ?: return
		val node = c.attributes["node"] ?: return
		val ver = c.attributes["ver"] ?: return

		if (cache.isCached("$node#$ver")) return

		discoModule.info(presence.from, "$node#$ver").response {
			if (it.isSuccess) storeInfo("$node#$ver", it.getOrThrow())
		}.send()
	}

	internal fun calculateVer(
		identities: List<DiscoveryModule.Identity>,
		features: List<String>,
		forms: List<JabberDataForm> = emptyList(),
	): String {
		val ids =
			identities.map { i -> i.category + "/" + i.type + "/" + (i.lang ?: "") + "/" + (i.name ?: "") }.sorted()
		val ftrs = features.sorted()

		val frms = forms.sortedBy { it.getFieldByVar("FORM_TYPE")?.fieldValue }.map { form ->
			(form.getAllFields().filter { it.fieldName == "FORM_TYPE" }
				.map { it.fieldValues.joinToString(separator = "<") { it } } + form.getAllFields()
				.filterNot { it.fieldName == "FORM_TYPE" }.sortedBy { it.fieldName }.map {
					it.fieldName + "<" + it.fieldValues.sorted().joinToString(separator = "<") { it }
				})
		}.flatten()

		val s = (ids + ftrs + frms).joinToString(separator = "<", postfix = "<")

		val hash = s.encodeToByteArray().sha1().bytes
		return Base64.encode(hash)
	}

	private fun processOutgoingPresence(stanza: Element) {
		if (stanza.getChildrenNS("c", XMLNS) != null) return

		val cElement = element("c") {
			xmlns = XMLNS
			attribute("hash", "sha-1")
			attribute("node", node)
			attribute("ver", getVerificationString())
		}
		stanza.add(cElement)

	}

	private fun storeInfo(node: String, info: DiscoveryModule.Info) {
		val isValid = validateVerificationString(info)
		if (!storeInvalid && !isValid) {
			log.warning("JID ${info.jid} provided invalid CAPS verification string. Skipping caching item.")
			return
		} else if (!isValid) {
			log.warning("JID ${info.jid} provided invalid CAPS verification string.")
		}
		val caps = Caps(node, info.identities, info.features)
		cache.store(node, caps)
	}

	fun validateVerificationString(info: DiscoveryModule.Info): Boolean {
		val calculatedVer = calculateVer(identities = info.identities, features = info.features, forms = info.forms)
		val receivedVer = info.node?.substringAfterLast("#")
		return receivedVer != null && calculatedVer == receivedVer
	}

	override fun afterReceive(element: Element): Element {
		if (element.name == Presence.NAME) {
			processIncomingPresence(element)
		}
		return element
	}

	override fun beforeSend(element: Element): Element {
		if (element.name == Presence.NAME) {
			processOutgoingPresence(element)
		}
		return element
	}

	/**
	 * Return server capabilities.
	 * @return [Caps] or `null` is capabilities are not received from server.
	 */
	fun getServerCapabilities(): Caps? {
		return getServerNode()?.let { serverNode ->
			cache.load(serverNode)
		}
	}

	/**
	 * Return entity capabilities based on [Presence] received from entity.
	 * @return [Caps] or `null` if capabilities are not provided in [Presence] or not discovered from entity yet.
	 */
	fun getCapabilities(presence: Presence): Caps? {
		val c: Element = presence.getChildrenNS("c", XMLNS) ?: return null
		val node = c.attributes["node"] ?: return null
		val ver = c.attributes["ver"] ?: return null
		return cache.load("$node#$ver")
	}

}
