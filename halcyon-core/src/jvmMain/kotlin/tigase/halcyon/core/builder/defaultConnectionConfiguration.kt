package tigase.halcyon.core.builder

import tigase.halcyon.core.configuration.Connection
import tigase.halcyon.core.connector.socket.DnsResolverMiniDns
import tigase.halcyon.core.connector.socket.SocketConnectorConfig
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

actual fun defaultConnectionConfiguration(accountBuilder: ConfigurationBuilder, defaultDomain: String): Connection = SocketConnectorConfig(
	hostname = defaultDomain
	?: throw ConfigurationException("Cannot determine domain name."),
	port = 5222,
	trustManager = object : X509TrustManager {
		override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
		}

		override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
		}

		override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
	},
	dnsResolver = DnsResolverMiniDns()
)