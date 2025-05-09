package tigase.halcyon.core.connector.socket

import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import tigase.halcyon.core.connector.ChannelBindingDataProvider
import tigase.halcyon.core.logger.LoggerFactory

/**
 * Default implementation of the TLSProcessor interface, used for handling TLS encryption in socket communication.
 *
 * @property socket The underlying socket used for communication.
 * @property config The configuration for the socket connector.
 */
class DefaultTLSProcessor(private val socket: Socket, private val config: SocketConnectorConfig) :
    TLSProcessor,
    ChannelBindingDataProvider {

    /**
     * Provides default implementation of the TLSProcessor interface, used for handling TLS encryption in socket
     * communication.
     *
     * @see TLSProcessor
     */
    companion object : TLSProcessorFactory {

        override val NAME: String = "DefaultTLSProcessor"

        override fun create(socket: Socket, config: SocketConnectorConfig): TLSProcessor =
            DefaultTLSProcessor(socket, config)
    }

    private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.JavaXTLSProcessor")

    private var secured: Boolean = false

    private var peerCertificates: Array<Certificate>? = null

    override fun isConnectionSecure(): Boolean = secured

    override fun getTlsUnique(): ByteArray? = null

    override fun getTlsServerEndpoint(): ByteArray? = peerCertificates?.first()?.let {
        calculateCertificateHash(it as X509Certificate)
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

    override fun proceedTLS(callback: TLSCallback) {
        val factory = getSocketFactory()
        val sslSocket = factory.createSocket(
            socket,
            config.hostname,
            socket.port,
            true
        ) as SSLSocket
        sslSocket.soTimeout = 0
        sslSocket.tcpNoDelay = true
        extendedSocketOptionsConfigurer?.invoke(sslSocket)
        log.info {
            "Processing TLS, SSLSocket: '$sslSocket' (${sslSocket.javaClass}) over: $socket (${socket.javaClass})"
        }
        if (!sslSocket.useClientMode) {
            // used `.createSocket()` factory method should already result in socket with clientMode set
            // but just in case it's not and to avoid Android exceptions let's to it like this
            sslSocket.useClientMode = true
        }
        sslSocket.addHandshakeCompletedListener { handshakeCompletedEvent ->
            log.info { "Handshake completed $handshakeCompletedEvent" }
            this.peerCertificates = handshakeCompletedEvent.session.peerCertificates
            if (!config.hostnameVerifier.verify(config.domain, this.peerCertificates!!.first())) {
                throw SSLHandshakeException(
                    "Certificate hostname doesn't match domain name you want to connect."
                )
            }
        }
        log.fine { "Starting handshake" }
        sslSocket.startHandshake()
        callback(sslSocket.inputStream, sslSocket.outputStream)
    }
}

fun calculateCertificateHash(certificate: X509Certificate): ByteArray? {
    val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.calculateCertificateHash")
    return try {
        val useAlgo = when (
            val sigAlgName = certificate.sigAlgName.substringBefore(
                "with"
            ).uppercase(Locale.getDefault())
        ) {
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
