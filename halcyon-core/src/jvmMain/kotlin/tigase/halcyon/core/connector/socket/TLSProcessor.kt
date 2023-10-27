package tigase.halcyon.core.connector.socket

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

typealias TLSCallback = (InputStream, OutputStream) -> Unit

interface TLSProcessor {

	fun proceedTLS(callback: TLSCallback)

	fun isConnectionSecure(): Boolean

	fun getTlsServerEndpoint(): ByteArray? = null

	fun getTlsUnique(): ByteArray? = null

	fun clear()

}

interface TLSProcessorFactory {

	fun create(socket: Socket, config: SocketConnectorConfig): TLSProcessor

}