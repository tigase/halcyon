package tigase.halcyon.core.connector.socket

import tigase.halcyon.core.connector.ChannelBindingDataProvider
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

typealias TLSCallback = (InputStream, OutputStream) -> Unit

/**
 * The TLSProcessor interface provides methods for handling TLS encryption and security operations.
 *
 * This interface extends the ChannelBindingDataProvider interface.
 */
interface TLSProcessor : ChannelBindingDataProvider {

	fun proceedTLS(callback: TLSCallback)

	override fun isConnectionSecure(): Boolean

	override fun getTlsServerEndpoint(): ByteArray? = null

	override fun getTlsUnique(): ByteArray? = null

	override fun getTlsExporter(): ByteArray? = null

	fun clear()

}

/**
 * The TLSProcessorFactory interface defines the contract for creating TLSProcessors.
 */
interface TLSProcessorFactory {

	/**
	 * The NAME variable represents the name TLS Processor.
	 *
	 * @see TLSProcessorFactory
	 * @see TLSProcessor
	 */
	val NAME: String

	/**
	 * Creates a TLSProcessor instance for the given socket and configuration.
	 *
	 * @param socket The socket to create a TLSProcessor for.
	 * @param config The SocketConnectorConfig containing the necessary configuration for TLS processing.
	 * @return The created TLSProcessor instance.
	 */
	fun create(socket: Socket, config: SocketConnectorConfig): TLSProcessor

}