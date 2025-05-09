package tigase.halcyon.core.xmpp.modules.serviceFinder

import tigase.halcyon.core.Context
import tigase.halcyon.core.modules.HalcyonModule
import tigase.halcyon.core.modules.HalcyonModuleProvider
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.toJID

interface ServiceFinderModuleConfig
class ServiceFinderModule(
    override val context: Context,
    private val discoveryModule: DiscoveryModule
) : HalcyonModule,
    ServiceFinderModuleConfig {

    companion object : HalcyonModuleProvider<ServiceFinderModule, ServiceFinderModuleConfig> {

        override val TYPE = "tigase:halcyon:service-finder"

        override fun instance(context: Context): ServiceFinderModule = ServiceFinderModule(
            context,
            discoveryModule = context.modules.getModule(DiscoveryModule)
        )

        override fun configure(
            module: ServiceFinderModule,
            cfg: ServiceFinderModuleConfig.() -> Unit
        ) = module.cfg()

        override fun requiredModules() = listOf(DiscoveryModule)
    }

    override val type = TYPE
    override val features = null

    private fun x(jid: JID, ctx: Ctx) {
        ctx.request(jid)
        discoveryModule.items(jid).response {
            it.onSuccess {
                it.items.map { it.jid }.distinct().forEach {
                    x(it, ctx)
                }
                ctx.finished(jid)
            }
            it.onFailure {
                ctx.finished(jid)
            }
        }.send()
    }

    class Ctx {

        private val requests = mutableListOf<JID>()

        fun finished(jid: JID) {
            requests.remove(jid)
        }

        fun request(jid: JID) {
            println("===== $jid")
            requests.add(jid)
        }
    }

    fun findComponents(
        predicate: (DiscoveryModule.Info) -> Boolean,
        resultHandler: (Result<List<DiscoveryModule.Info>>) -> Unit
    ) {
        val domain = context.boundJID?.bareJID?.domain!!
        val toCheck = mutableSetOf<JID>()
        val resultList = mutableListOf<DiscoveryModule.Info>()
        var counter = 0

        discoveryModule.items(domain.toJID()).response {
            it.onSuccess { itemsResp ->
                counter = itemsResp.items.size
                toCheck.addAll(itemsResp.items.map { it.jid })
                itemsResp.items.map { it.jid }.forEach { jid ->
                    discoveryModule.info(jid).response { infoResp ->
                        toCheck.remove(jid)
                        --counter
                        infoResp.onSuccess {
                            resultList.add(it)
                        }
                        if (counter ==
                            0
                        ) {
                            resultHandler.invoke(Result.success(resultList.filter(predicate)))
                        }
                    }.send()
                }
            }
            it.onFailure {
                resultHandler.invoke(Result.failure(it))
            }
        }.send()
    }
}
