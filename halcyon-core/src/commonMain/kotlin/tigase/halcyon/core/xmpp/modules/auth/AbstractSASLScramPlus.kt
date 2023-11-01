package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.Context
import tigase.halcyon.core.configuration.Configuration
import tigase.halcyon.core.connector.ChannelBindingDataProvider
import tigase.halcyon.core.xml.Element

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

	private fun checkAvailability(
		context: Context, data: SCRAMData, type: BindType, bindingData: (Context) -> ByteArray?
	): Pair<BindType, ByteArray>? {
		data.bindTypesSupportedByServer?.let {
			if (!it.contains(type)) return null
		}

		val cbd = bindingData(context) ?: return null
		return Pair(type, cbd)
	}

	override fun prepareChannelBindingData(
		context: Context, config: Configuration, saslContext: SASLContext
	): Pair<BindType, ByteArray> {
		val data = scramData(saslContext)

		checkAvailability(context, data, BindType.TlsUnique, tlsUniqueProvider)?.let { return it }
		checkAvailability(context, data, BindType.TlsExporter, tlsExporterProvider)?.let { return it }
		checkAvailability(context, data, BindType.TlsServerEndPoint, serverEndpointProvider)?.let { return it }

		return super.prepareChannelBindingData(context, config, saslContext)
	}

	private fun allowedChannelBindingTypes(streamFeatures: Element): List<BindType>? {
		val names = streamFeatures.getChildrenNS("sasl-channel-binding", "urn:xmpp:sasl-cb:0")?.children?.filter {
			it.name == "channel-binding"
		}?.mapNotNull { it.attributes["type"] } ?: return null
		return names.mapNotNull { tp -> BindType.values().find { it.xmlValue == tp } }
	}

	override fun isAllowedToUse(
		context: Context, config: Configuration, saslContext: SASLContext, streamFeatures: Element
	): Boolean {
		val c = (context as AbstractHalcyon).connector ?: return false
		if (c !is ChannelBindingDataProvider || !c.isConnectionSecure()) return false

		val data = scramData(saslContext)
		data.bindTypesSupportedByServer = allowedChannelBindingTypes(streamFeatures)

		val bindingType = prepareChannelBindingData(context, config, saslContext).first

		if (bindingType == BindType.N) return false

		return super.isAllowedToUse(context, config, saslContext, streamFeatures)
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
	return provider.getTlsExporter()
}