package tigase.halcyon.core.connector.socket

import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Locale
import javax.security.auth.x500.X500Principal
import tigase.halcyon.core.logger.LoggerFactory

class DefaultHostnameVerifier : XMPPHostnameVerifier {

    private val log = LoggerFactory.logger(
        "tigase.halcyon.core.connector.socket.DefaultHostnameVerifier"
    )

    override fun verify(domainName: String, certificate: Certificate): Boolean =
        if (certificate is X509Certificate) {
            validateCertificate(domainName, certificate)
        } else {
            log.warning { "Unsupported certificate type." }
            false
        }

    protected fun match(hostname: String, altName: String?): Boolean {
        if (hostname.isEmpty() || altName.isNullOrEmpty()) {
            return false
        }
        val normalizedAltName = altName.lowercase()
        if (!normalizedAltName.contains("*")) {
            return hostname == normalizedAltName
        }
        if (normalizedAltName.startsWith("*.") &&
            hostname.regionMatches(
                0,
                normalizedAltName,
                2,
                normalizedAltName.length - 2
            )
        ) {
            return true
        }
        val asteriskIdx = normalizedAltName.indexOf('*')
        val dotIdx = normalizedAltName.indexOf('.')
        if (asteriskIdx > dotIdx) {
            return false
        }
        if (!hostname.regionMatches(0, normalizedAltName, 0, asteriskIdx)) {
            return false
        }
        val suffixLength = normalizedAltName.length - (asteriskIdx + 1)
        val suffixStart = hostname.length - suffixLength
        return if (hostname.indexOf('.', asteriskIdx) < suffixStart) {
            false // wildcard '*' can't match a '.'
        } else {
            hostname.regionMatches(suffixStart, normalizedAltName, asteriskIdx + 1, suffixLength)
        }
    }

    private fun validateCertificate(domain: String, certificate: X509Certificate): Boolean {
        var altNamePresents = false
        certificate.subjectAlternativeNames?.filterNotNull()?.forEach { entry ->
            val altNameType = entry[0] as Int
            if (altNameType == 2) {
                altNamePresents = true
                val altName = entry[1] as String
                if (match(domain, altName)) {
                    return true
                }
            }
        }

        if (!altNamePresents) {
            val principal: X500Principal = certificate.getSubjectX500Principal()
            val cn: String? = extractCN(principal)
            if (cn != null) {
                return match(domain, cn)
            }
        }

        return false
    }

    protected fun extractCN(principal: X500Principal): String? {
        val dd =
            principal.getName(X500Principal.RFC2253).split(",".toRegex()).dropLastWhile {
                it.isEmpty()
            }.toTypedArray()
        for (string in dd) {
            if (string.lowercase(Locale.getDefault()).startsWith("cn=")) {
                return string.substring(3)
            }
        }
        return null
    }
}
