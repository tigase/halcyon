package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.Context
import tigase.halcyon.core.configuration.Configuration
import tigase.halcyon.core.connector.ChannelBindingDataProvider

abstract class AbstractSASLScramPlus(
	name: String,
	hashAlgorithm: ScramHashAlgorithm,
	randomGenerator: () -> String,
	clientKeyData: ByteArray = "Client Key".encodeToByteArray(),
	serverKeyData: ByteArray = "Server Key".encodeToByteArray(),
	private val tlsUniqueProvider: (Context) -> ByteArray? = ::retrieveTlsUnique,
	private val serverEndpointProvider: (Context) -> ByteArray? = ::retrieveTlsServerEndpoint,
	private val tlsExporterProvider: (Context) -> ByteArray? = ::retrieveTlsExporter
) : AbstractSASLScram(name, hashAlgorithm, randomGenerator, clientKeyData, serverKeyData) {


	override fun prepareChannelBindingData(
		context: Context, config: Configuration, saslContext: SASLContext
	): Pair<BindType, ByteArray> {
		tlsExporterProvider(context)?.let {
			return Pair(BindType.TlsExporter, it)
		}
		tlsUniqueProvider(context)?.let {
			return Pair(BindType.TlsUnique, it)
		}
		serverEndpointProvider(context)?.let {
			return Pair(BindType.TlsServerEndPoint, it)
		}
		return super.prepareChannelBindingData(context, config, saslContext)
	}

	override fun isAllowedToUse(context: Context, config: Configuration, saslContext: SASLContext): Boolean {
		val c = (context as AbstractHalcyon).connector ?: return false
		if (c !is ChannelBindingDataProvider || !c.isConnectionSecure()) return false

		return super.isAllowedToUse(context, config, saslContext)
	}

}

private fun retrieveTlsUnique(context: Context): ByteArray? {
	val provider = (context as AbstractHalcyon).connector as ChannelBindingDataProvider
	return provider.getTlsUnique()
}

private fun retrieveTlsServerEndpoint(context: Context): ByteArray? {
	val provider = (context as AbstractHalcyon).connector as ChannelBindingDataProvider
	return provider.getTlsServerEndpoint()
}

private fun retrieveTlsExporter(context: Context): ByteArray? {
	val provider = (context as AbstractHalcyon).connector as ChannelBindingDataProvider
	return provider.getTlsServerEndpoint()
}