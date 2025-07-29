package tigase.halcyon.core.connector

data class SrvRecord(val target: String, val port: UInt, val weight: UInt, val priority: UInt)

interface DnsResolver {

    fun resolve(domain: String, completionHandler: (Result<List<SrvRecord>>) -> Unit)
}
