package tigase.halcyon.core.connector.socket

import org.bouncycastle.tls.*
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import tigase.halcyon.core.logger.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager

/**
 * The BouncyCastleTLSProcessor class implements the TLSProcessor interface for handling TLS encryption and security operations
 * using the Bouncy Castle library. This TLS Processor allows to use `tls-exporter`, and `tls-unique` channel binding
 * in SASL SCRAM.
 *
 * @param socket The socket to create a TLSProcessor for.
 * @param config The SocketConnectorConfig containing the necessary configuration for TLS processing.
 */
class BouncyCastleTLSProcessor(private val socket: Socket, private val config: SocketConnectorConfig) : TLSProcessor {

	private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.BouncyCastleTLSProcessor")

	/**
	 * The BouncyCastleTLSProcessor class implements the TLSProcessor interface for handling TLS encryption and security operations
	 * using the Bouncy Castle library. This TLS Processor allows to use `tls-exporter`, and `tls-unique` channel
	 * binding in SASL SCRAM.
	 */
	companion object : TLSProcessorFactory {

		override val NAME: String = "BouncyCastleTLSProcessor"

		override fun create(socket: Socket, config: SocketConnectorConfig): TLSProcessor =
			BouncyCastleTLSProcessor(socket, config)
	}

	private var peerCertificates: Array<X509Certificate>? = null

	private var secured: Boolean = false

	private var tlsUnique: ByteArray? = null

	private var tlsServerEndpoint: ByteArray? = null

	private var tlsExporter: ByteArray? = null

	override fun getTlsUnique(): ByteArray? = tlsUnique

	override fun getTlsServerEndpoint(): ByteArray? {
		if (this.tlsServerEndpoint != null) return this.tlsServerEndpoint
		this.tlsServerEndpoint = peerCertificates?.first()?.let { calculateCertificateHash(it) }
		return tlsServerEndpoint
	}

	override fun getTlsExporter(): ByteArray? = tlsExporter

	override fun isConnectionSecure(): Boolean = secured

	override fun clear() {
		secured = false
	}

	override fun proceedTLS(callback: TLSCallback) {
		log.info("Proceeding TLS with Bouncycastle")

		val tlsCrypto = BcTlsCrypto(SecureRandom())
		val tlsClientProtocol = TlsClientProtocol(
			socket.getInputStream(), socket.getOutputStream()
		)

		val tlsClient: DefaultTlsClient = object : DefaultTlsClient(tlsCrypto) {
			override fun getAuthentication(): TlsAuthentication = XMPPServerAuthentication()
			override fun notifyHandshakeComplete() {
				super.notifyHandshakeComplete()
				secured = true
				this@BouncyCastleTLSProcessor.tlsExporter = context.exportChannelBinding(ChannelBinding.tls_exporter)
				this@BouncyCastleTLSProcessor.tlsUnique = context.exportChannelBinding(ChannelBinding.tls_unique)
				context.exportChannelBinding(ChannelBinding.tls_server_end_point)?.let {
					this@BouncyCastleTLSProcessor.tlsServerEndpoint = it
				}

			}
		}
		tlsClientProtocol.connect(tlsClient)
		callback(tlsClientProtocol.inputStream, tlsClientProtocol.outputStream)
	}

	@Throws(CertificateException::class, IOException::class)
	private fun convertChain(certificates: Certificate): Array<X509Certificate> {
		val result = mutableListOf<X509Certificate>()
		for (i in 0 until certificates.length) {
			val cert = certificates.getCertificateAt(i)
			val jsCert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(cert.encoded))
			result += jsCert as X509Certificate
		}
		return result.toTypedArray()
	}

	protected fun getAuthType(tlsKeyExchange: TlsKeyExchange?): String? {
		return try {
			val keyExchangeField = AbstractTlsKeyExchange::class.java.getDeclaredField("keyExchange")
			keyExchangeField.setAccessible(true)
			val v = keyExchangeField[tlsKeyExchange]
			when (val i = v.toString().toInt()) {
				0 -> "NULL"
				1 -> "RSA"
				2 -> "RSA_EXPORT"
				3 -> "DHE_DSS"
				4 -> "DHE_DSS_EXPORT"
				5 -> "DHE_RSA"
				6 -> "DHE_RSA_EXPORT"
				7 -> "DH_DSS"
				8 -> "DH_DSS_EXPORT"
				9 -> "DH_RSA"
				10 -> "DH_RSA_EXPORT"
				11 -> "DH_anon"
				12 -> "DH_anon_EXPORT"
				13 -> "PSK"
				14 -> "DHE_PSK"
				15 -> "RSA_PSK"
				16 -> "ECDH_ECDSA"
				17 -> "ECDHE_ECDSA"
				18 -> "ECDH_RSA"
				19 -> "ECDHE_RSA"
				20 -> "ECDH_anon"
				21 -> "SRP"
				22 -> "SRP_DSS"
				23 -> "SRP_RSA"
				24 -> "ECDHE_PSK"
				else -> "UNKNOWN $i"
			}
		} catch (e: Throwable) {
			e.printStackTrace()
			null
		}
	}

	inner class XMPPServerAuthentication : ServerOnlyTlsAuthentication() {

		override fun notifyServerCertificate(serverCertificate: TlsServerCertificate?) {
			this@BouncyCastleTLSProcessor.peerCertificates = serverCertificate?.certificate?.let { convertChain(it) }
				?: throw SSLHandshakeException("Unrecognized server certificates list.")


			if (config.trustManager is X509TrustManager) {
				(config.trustManager as X509TrustManager).checkServerTrusted(peerCertificates!!, "")
			}

			if (!config.hostnameVerifier.verify(config.domain, peerCertificates!!.first())) {
				throw SSLHandshakeException(
					"Certificate hostname doesn't match domain name you want to connect."
				)
			}
		}
	}

}