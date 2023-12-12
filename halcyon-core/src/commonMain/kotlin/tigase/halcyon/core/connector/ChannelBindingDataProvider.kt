package tigase.halcyon.core.connector

/**
 * The ChannelBindingDataProvider interface provides methods for retrieving channel binding data in TLS connections.
 */
interface ChannelBindingDataProvider {

	/**
	 * Retrieves the `tls-unique` channel binding data.
	 *
	 * @return the `tls-unique` channel binding data as a byte array, or null if it is unavailable.
	 */
	fun getTlsUnique(): ByteArray?

	/**
	 * Retrieves the `tls-server-end-point` channel binding data.
	 *
	 * @return the `tls-server-end-point` channel binding data as a byte array, or null if it is unavailable.
	 */
	fun getTlsServerEndpoint(): ByteArray?

	/**
	 * Retrieves the `tls-exporter` channel binding data.
	 *
	 * @return the `tls-exporter` channel binding data as a byte array, or null if it is unavailable.
	 */
	fun getTlsExporter(): ByteArray?

	/**
	 * Checks if the connection is secure.
	 *
	 * @return true if the connection is secure, false otherwise.
	 */
	fun isConnectionSecure(): Boolean

}