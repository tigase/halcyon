package tigase.halcyon.core.builder

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import tigase.halcyon.core.connector.DnsResolver
import tigase.halcyon.core.connector.socket.*

/**
 * Builder class for creating a SocketConnectorConfig instance for configuring a socket connection.
 */
@HalcyonConfigDsl
class SocketConnectionBuilder : ConnectionConfigItemBuilder<SocketConnectorConfig> {

    var hostname: String? = null

    var port: Int = 5222

    /**
     * trustManager is a variable of type X509TrustManager? that is used to specify the X509TrustManager implementation for TLS certificate verification in a socket connection.
     *
     * X509TrustManager is an interface that defines the contract for verifying the authenticity of X.509 certificates in a TLS connection. It provides methods for checking the trust
     *worthiness of both client and server certificates.
     *
     * This variable can be assigned a custom X509TrustManager implementation or left as null. If it is null, a default TrustManager implementation will be used, which does not perform
     * any certificate verification and accepts all certificates.
     *
     * Usage:
     * trustManager is used within the build() function of the SocketConnectionBuilder class to create a SocketConnectorConfig instance. The SocketConnectorConfig represents the configuration
     * for a socket connection, including the trustManager to be used for TLS certificate verification.
     *
     * Example Usage:
     * val socketConnectionBuilder = SocketConnectionBuilder()
     * socketConnectionBuilder.trustManager = MyCustomTrustManager()
     * val socketConnectorConfig = socketConnectionBuilder.build(configurationBuilder, defaultDomain)
     *
     * @see X509TrustManager
     * @see SocketConnectionBuilder
     * @see SocketConnectorConfig
     */
    var trustManager: X509TrustManager? = null

    /**
     * The `dnsResolver` variable is of type `DnsResolver` and is used to resolve domain names to a list of SRV records.
     */
    var dnsResolver: DnsResolver = DnsResolverMiniDns()

    /**
     * The `hostnameVerifier` variable is used to specify the hostname verification strategy for a socket connection. It is of type `XMPPHostnameVerifier`, which is an interface that
     * defines the contract for verifying the hostname of a TLS certificate.
     */
    var hostnameVerifier: XMPPHostnameVerifier = DefaultHostnameVerifier()

    /**
     * Factory for creating TLSProcessors.
     *
     * @see TLSProcessor
     */
    var tlsProcessorFactory: TLSProcessorFactory = DefaultTLSProcessorFactory

    override fun build(root: ConfigurationBuilder, defaultDomain: String?): SocketConnectorConfig =
        SocketConnectorConfig(
            hostname = hostname,
            domain = defaultDomain ?: throw ConfigurationException("Cannot determine domain name."),
            port = port,
            trustManager = trustManager ?: object : X509TrustManager {
                override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
                }

                override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            },
            dnsResolver = dnsResolver,
            hostnameVerifier = hostnameVerifier,
            tlsProcessorFactory = tlsProcessorFactory
        )
}

/**
 * Configures a socket connector for the Halcyon configuration.
 *
 * @param init the initialization block for configuring the socket connector
 */
fun ConfigurationBuilder.socketConnector(init: SocketConnectionBuilder.() -> Unit) {
    val n = SocketConnectionBuilder()
    n.init()
    this.connection = n
}
