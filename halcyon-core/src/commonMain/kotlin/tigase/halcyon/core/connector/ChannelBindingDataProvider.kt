package tigase.halcyon.core.connector

interface ChannelBindingDataProvider {

	fun getTlsUnique(): ByteArray?

	fun getTlsServerEndpoint(): ByteArray?

	fun isConnectionSecure(): Boolean

}