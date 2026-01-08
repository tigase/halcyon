package tigase.halcyon.core.connector.socket

import korlibs.encoding.base64
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CalculateCertificateHashTest {

	private fun loadCert(name: String): X509Certificate = this.javaClass.getResource(name)?.openStream().use {
		CertificateFactory.getInstance("X.509").generateCertificate(it)
	} as X509Certificate

	private fun hextobase64(data: String): String =
		data.replace(Regex("[ :]"), "").chunked(2).map { it.toInt(16).toByte() }.toByteArray().base64

	@Test
	fun testHashCalculation() {
		loadCert("/AppleRootCA.cer").let {
			assertNotNull(calculateCertificateHash(it)) {
				assertEquals(
					hextobase64("B0 B1 73 0E CB C7 FF 45 05 14 2C 49 F1 29 5E 6E DA 6B CA ED 7E 2C 68 C5 BE 91 B5 A1 10 01 F0 24"),
					it.base64,
					"Invalid Apple certificate hash"
				)
				assertEquals(
					"sLFzDsvH/0UFFCxJ8Slebtpryu1+LGjFvpG1oRAB8CQ=", it.base64, "Invalid Apple certificate hash"
				)
			}
		}
		loadCert("/CertumTrustedNetworkCA2.cer").let {
			assertNotNull(calculateCertificateHash(it)) {
				assertEquals(
					hextobase64("04:CB:F2:A5:F7:40:D0:30:20:81:36:B0:EE:1D:B3:82:99:94:3C:74:EF:A5:50:45:F5:64:26:82:46:A9:29:01:8F:CA:F2:6A:A0:27:68:BB:20:32:1A:A3:F7:0C:46:09:C1:63:C7:5A:39:29:EF:8D:A0:16:DE:00:05:66:A7:4C"),
					it.base64,
					"Invalid Apple certificate hash"
				)
			}
		}
	}

}