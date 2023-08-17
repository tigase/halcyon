package tigase.halcyon.core.xmpp.modules.discoaltconn

import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.HalcyonModule
import tigase.halcyon.core.modules.HalcyonModuleProvider
import tigase.halcyon.core.xml.parser.parseXMLOrNull

/**
 * Configuration of [AlternativeConnectionMethodModule]
 */
@HalcyonConfigDsl
interface AlternativeConnectionMethodModuleConfig

data class HostLink(val rel: String, val href: String)

/**
 * Module is implementing Discovering Alternative XMPP Connection Methods ([XEP-0156](https://xmpp.org/extensions/xep-0156.html)).
 *
 */
class AlternativeConnectionMethodModule(override val context: Context) : HalcyonModule,
	AlternativeConnectionMethodModuleConfig {

	companion object :
		HalcyonModuleProvider<AlternativeConnectionMethodModule, AlternativeConnectionMethodModuleConfig> {

		override val TYPE = "urn:xmpp:alt-connections"

		override fun instance(context: Context): AlternativeConnectionMethodModule =
			AlternativeConnectionMethodModule(context)

		override fun configure(
			module: AlternativeConnectionMethodModule, cfg: AlternativeConnectionMethodModuleConfig.() -> Unit
		) = module.cfg()
	}

	override val type = TYPE
	override val features = null
	private val log =
		LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.discoaltconn.AlternativeConnectionMethodModule")


	fun discovery(domain: String, callback: (List<HostLink>) -> Unit) {
		val url = "https://$domain/.well-known/host-meta"
		log.finer { "Loading host info from $url" }
		loadRemoteContent(url) {
			log.finer { "Received: $it" }
			if (it.isBlank()) callback(emptyList())
			val element = parseXMLOrNull(it)
			if (element == null) {
				callback(emptyList())
			} else {
				val result = element.getChildren("Link").map {
					HostLink(
						rel = it.attributes["rel"]!!, href = it.attributes["href"]!!
					)
				}
				callback(result)
			}
		}
	}
}