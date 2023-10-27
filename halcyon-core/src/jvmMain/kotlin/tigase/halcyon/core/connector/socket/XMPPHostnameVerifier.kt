package tigase.halcyon.core.connector.socket

import java.security.cert.Certificate

interface XMPPHostnameVerifier {

	fun verify(domainName: String, certificate: Certificate): Boolean

}