package tigase.halcyon.core.connector.socket

import tigase.halcyon.core.connector.ChannelBindingDataProvider
import tigase.halcyon.core.logger.LoggerFactory
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class JavaXTLSProcessor(private val socket: Socket, private val config: SocketConnectorConfig) : TLSProcessor,
	ChannelBindingDataProvider {

	companion object : TLSProcessorFactory {

		override fun create(socket: Socket, config: SocketConnectorConfig): TLSProcessor =
			JavaXTLSProcessor(socket, config)
	}

	private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.JavaXTLSProcessor")

	private var secured: Boolean = false

	private var peerCertificates: Array<Certificate>? = null

	override fun isConnectionSecure(): Boolean = secured

	override fun getTlsUnique(): ByteArray? = null

	override fun getTlsServerEndpoint(): ByteArray? {
		return peerCertificates?.first()?.let { calculateCertificateHash(it as X509Certificate) }
	}

	override fun getTlsExporter(): ByteArray? = null

	override fun clear() {
		secured = false
		peerCertificates = null
	}

	private fun getSocketFactory(): SSLSocketFactory {
		val ctx = SSLContext.getInstance("TLS")

		ctx.init(emptyArray(), arrayOf(config.trustManager), SecureRandom())

		return ctx.socketFactory
	}

	private val lock = Object()

	override fun proceedTLS(callback: TLSCallback) {
		val factory = getSocketFactory()
		val s1 = factory.createSocket(socket, config.hostname, socket.port, true) as SSLSocket
		s1.soTimeout = 0
		s1.keepAlive = false
		s1.tcpNoDelay = true
		s1.useClientMode = true
		s1.addHandshakeCompletedListener { handshakeCompletedEvent ->
			log.info { "Handshake completed $handshakeCompletedEvent" }
			this.peerCertificates = handshakeCompletedEvent.session.peerCertificates
			this.secured = true
			synchronized(lock) {
				lock.notify()
			}
		}
		s1.startHandshake()
		synchronized(lock) {
			if (!secured) lock.wait(100)
		}

		log.info { "Verifying domain name" }
		if (!config.hostnameVerifier.verify(config.domain, this.peerCertificates!!.first())) {
			throw SSLHandshakeException(
				"Certificate hostname doesn't match domain name you want to connect."
			)
		}

		callback(s1.inputStream, s1.outputStream)
	}

}

fun calculateCertificateHash(certificate: X509Certificate): ByteArray? {
	val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.calculateCertificateHash")
	return try {
		val sigAlgName = certificate.sigAlgName.substringBefore("with").uppercase(Locale.getDefault())
		val useAlgo = when (sigAlgName) {
			"MD5", "SHA1" -> "SHA-256"
			"SHA224" -> "SHA-224"
			"SHA256" -> "SHA-256"
			"SHA384" -> "SHA-384"
			"SHA512" -> "SHA-512"
			else -> sigAlgName
		}
		log.finer { "Calculating hash of certificate with $useAlgo algorithm." }
		MessageDigest.getInstance(useAlgo).digest(certificate.encoded)
	} catch (e: Exception) {
		log.severe(e) { "Cannot calculate certificate hash." }
		e.printStackTrace()
		null
	}
}