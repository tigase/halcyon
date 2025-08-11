package tigase.halcyon.core.connector.socket

import org.minidns.hla.DnssecResolverApi
import org.minidns.hla.srv.SrvProto
import org.minidns.hla.srv.SrvService
import tigase.halcyon.core.connector.DnsResolver
import tigase.halcyon.core.connector.SrvRecord
import tigase.halcyon.core.exceptions.HalcyonException

class DnsResolverMiniDns : DnsResolver {

	override fun resolve(domain: String, completionHandler: (Result<List<SrvRecord>>) -> Unit) {
		try {
			val res = DnssecResolverApi.INSTANCE.resolveSrv(SrvService.xmpp_client, SrvProto.tcp, domain)
            val resSsl = DnssecResolverApi.INSTANCE.resolveSrv(SrvService.xmpps_client, SrvProto.tcp, domain)

			if ((!res.wasSuccessful()) && (!resSsl.wasSuccessful())) {
				completionHandler.invoke(Result.failure(HalcyonException("Cannot retrieve domain $domain. responseCode=${res.responseCode}")))
			} else {
                (res.answers.map {
                    SrvRecord(
                        port = it.port.toUInt(),
                        weight = it.weight.toUInt(),
                        priority = it.priority.toUInt(),
                        target = it.target.toString(),
                        directTls = false
                    )
                } + resSsl.answers.map {
                    SrvRecord(
                        port = it.port.toUInt(),
                        weight = it.weight.toUInt(),
                        priority = it.priority.toUInt(),
                        target = it.target.toString(),
                        directTls = true
                    )
                }).let { completionHandler.invoke(Result.success(it)) }
			}
		} catch (e: Exception) {
			completionHandler.invoke(Result.failure(HalcyonException("Cannot retrieve domain $domain. Exception ${e} ")))
		}
	}
}
