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

import kotlinx.serialization.Serializable
import tigase.halcyon.core.*
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.HasInterceptors
import tigase.halcyon.core.modules.StanzaInterceptor
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.StreamFeaturesModule
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.modules.discovery.NodeDetailsProvider
import tigase.halcyon.core.xmpp.stanzas.Presence
import tigase.halcyon.core.xmpp.stanzas.wrap

class EntityCapabilitiesModule(override val context: Context) : XmppModule, HasInterceptors, StanzaInterceptor {

	@Serializable
	data class Caps(
		val node: String, val identities: List<DiscoveryModule.Identity>, val features: List<String>,
	)

	companion object {

		const val XMLNS = "http://jabber.org/protocol/caps"
		const val TYPE = XMLNS
	}

	override val type: String = TYPE
	override val criteria: Criteria? = null
	override val stanzaInterceptors: Array<StanzaInterceptor> = arrayOf(this)
	override val features: Array<String> = arrayOf(XMLNS)

	private lateinit var discoModule: DiscoveryModule
	private lateinit var streamFeaturesModule: StreamFeaturesModule
	private lateinit var bindModule: BindModule

	var node: String = "http://tigase.org/TigaseHalcyon"
	var cache: EntityCapabilitiesCache = DefaultEntityCapabilitiesCache()

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

	override fun initialize() {
		this.discoModule = context.modules.getModule(DiscoveryModule.TYPE)
		this.discoModule.addNodeDetailsProvider(CapsNodeDetailsProvider())
		this.streamFeaturesModule = context.modules.getModule(StreamFeaturesModule.TYPE)
		this.bindModule = context.modules.getModule(BindModule.TYPE)

		context.eventBus.register<HalcyonStateChangeEvent>(HalcyonStateChangeEvent.TYPE) {
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
			val jid = bindModule.boundJID ?: return
			if (cache.isCached(node)) return
			discoModule.info(JID.parse(jid.domain), node).response {
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

	internal fun calculateVer(identities: List<DiscoveryModule.Identity>, features: List<String>): String {
		val ids = identities.map { i -> i.category + "/" + i.type + "//" + i.name }.sorted()
			.joinToString(separator = "<", postfix = "<")
		val ftrs = features.sorted().joinToString(separator = "<", postfix = "<")
		val s = "$ids$ftrs"

		val hash = hashSHA1(s.encodeToByteArray())
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
		cache.store(node, Caps(node, info.identities, info.features))
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

	fun getServerCapabilities(): Caps? {
		return getServerNode()?.let { serverNode ->
			cache.load(serverNode)
		}
	}

	fun getCapabilities(presence: Presence): Caps? {
		val c: Element = presence.getChildrenNS("c", XMLNS) ?: return null
		val node = c.attributes["node"] ?: return null
		val ver = c.attributes["ver"] ?: return null
		return cache.load("$node#$ver")
	}

}

expect fun hashSHA1(buffer: ByteArray): ByteArray