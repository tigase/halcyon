package tigase.halcyon.core.xmpp.modules.auth

import com.soywiz.krypto.PBKDF2
import com.soywiz.krypto.encoding.base64
import com.soywiz.krypto.encoding.fromBase64
import tigase.halcyon.core.Base64
import tigase.halcyon.core.builder.createConfiguration
import tigase.halcyon.core.xmpp.toBareJID
import kotlin.test.*

class SASLScramSHATest {

	@Test
	fun test_hi_from_soywiz() {
		assertEquals(
			"n3jX3vxtWYywWAJWKRt1SH73XEHd+5x7bUVlwI3mTeI=", PBKDF2.pbkdf2WithHmacSHA256(
				"a".encodeToByteArray(), Base64.decodeToByteArray("QSXCR+Q6sek8bf92"), 1024, 256
			).base64
		)
		assertEquals(
			"rk+0v0g6+r7NEX/w5STcHxccFyNbwkorOUNtyOkOsyA=", PBKDF2.pbkdf2WithHmacSHA256(
				"aa".encodeToByteArray(), Base64.decodeToByteArray("QSXCR+Q6sek8bf92"), 1024, 256
			).base64
		)
		assertEquals(
			"eSexmOCD3+4+GYv7o0C158OFU/lIrpUDxtxlQrWQe7E=", PBKDF2.pbkdf2WithHmacSHA256(
				"aaa".encodeToByteArray(), Base64.decodeToByteArray("QSXCR+Q6sek8bf92"), 1024, 256
			).base64
		)
		assertEquals(
			"/A7+s5RfyLiJhkDrZrMA6QuYZoWtSMNzLGasrxVM1SA=", PBKDF2.pbkdf2WithHmacSHA256(
				"aaaa".encodeToByteArray(), Base64.decodeToByteArray("QSXCR+Q6sek8bf92"), 1024, 256
			).base64
		)

		assertEquals(
			"vCLuivE7J56jv5TT3DSusTzLWDeaM6RFxYJFKMEVJ6w=", PBKDF2.pbkdf2WithHmacSHA256(
				"a".encodeToByteArray(), Base64.decodeToByteArray("PxR/wv+epq"), 1024, 256
			).base64
		)
		assertEquals(
			"1Q1otjo/Bv+v9jLmMT9pWX2gG9HBq5LqgyFRkdSiGio=", PBKDF2.pbkdf2WithHmacSHA256(
				"aa".encodeToByteArray(), Base64.decodeToByteArray("PxR/wv+epq"), 1024, 256
			).base64
		)
		assertEquals(
			"ky3MAhegAZbI0BuXmbdgAtS9o1+Jju441H/taRHnJyw=", PBKDF2.pbkdf2WithHmacSHA256(
				"aaa".encodeToByteArray(), Base64.decodeToByteArray("PxR/wv+epq"), 1024, 256
			).base64
		)
		assertEquals(
			"7P3B8R6LG6mA0Ipout5ZcMsj9HjHvekVltXe2LwgvGw=", PBKDF2.pbkdf2WithHmacSHA256(
				"aaaa".encodeToByteArray(), Base64.decodeToByteArray("PxR/wv+epq"), 1024, 256
			).base64
		)
	}

	@Test
	fun test_hi() {
		println(
			PBKDF2.pbkdf2WithHmacSHA256(
				"a".encodeToByteArray(), Base64.decodeToByteArray("QSXCR+Q6sek8bf92"), 1024, 256
			).base64
		)

		assertEquals(
			"n3jX3vxtWYywWAJWKRt1SH73XEHd+5x7bUVlwI3mTeI=",
			Base64.encode(hi("a".encodeToByteArray(), Base64.decodeToByteArray("QSXCR+Q6sek8bf92"), 1024))
		)
		assertEquals(
			"rk+0v0g6+r7NEX/w5STcHxccFyNbwkorOUNtyOkOsyA=",
			Base64.encode(hi("aa".encodeToByteArray(), Base64.decodeToByteArray("QSXCR+Q6sek8bf92"), 1024))
		)
		assertEquals(
			"eSexmOCD3+4+GYv7o0C158OFU/lIrpUDxtxlQrWQe7E=",
			Base64.encode(hi("aaa".encodeToByteArray(), Base64.decodeToByteArray("QSXCR+Q6sek8bf92"), 1024))
		)
		assertEquals(
			"/A7+s5RfyLiJhkDrZrMA6QuYZoWtSMNzLGasrxVM1SA=",
			Base64.encode(hi("aaaa".encodeToByteArray(), Base64.decodeToByteArray("QSXCR+Q6sek8bf92"), 1024))
		)
		assertEquals(
			"vCLuivE7J56jv5TT3DSusTzLWDeaM6RFxYJFKMEVJ6w=",
			Base64.encode(hi("a".encodeToByteArray(), Base64.decodeToByteArray("PxR/wv+epq"), 1024))
		)
		assertEquals(
			"1Q1otjo/Bv+v9jLmMT9pWX2gG9HBq5LqgyFRkdSiGio=",
			Base64.encode(hi("aa".encodeToByteArray(), Base64.decodeToByteArray("PxR/wv+epq"), 1024))
		)
		assertEquals(
			"ky3MAhegAZbI0BuXmbdgAtS9o1+Jju441H/taRHnJyw=",
			Base64.encode(hi("aaa".encodeToByteArray(), Base64.decodeToByteArray("PxR/wv+epq"), 1024))
		)
		assertEquals(
			"7P3B8R6LG6mA0Ipout5ZcMsj9HjHvekVltXe2LwgvGw=",
			Base64.encode(hi("aaaa".encodeToByteArray(), Base64.decodeToByteArray("PxR/wv+epq"), 1024))
		)
	}

	@Test
	fun test_first_message_with_authcid() {
		val scram = SASLScramSHA1(randomGenerator = {
			"fyko+d2lbbFgONRv9qkxdawL"
		})

		val configuration = createConfiguration {
			auth {
				userJID = "user@example.com".toBareJID()
				authenticationName = "differentusername"
				password { "pencil" }
			}
		}.build()
		val context = SASLContext()

		// first client message
		assertEquals(
			"n,a=user@example.com,n=differentusername,r=fyko+d2lbbFgONRv9qkxdawL",
			scram.evaluateChallenge(null, configuration, context)!!
				.fromBase64()
				.decodeToString(),
			"Invalid first client message"
		)

	}

	@Test
	fun test_first_message_with_authcid_equals_localpart() {
		val scram = SASLScramSHA1(randomGenerator = {
			"fyko+d2lbbFgONRv9qkxdawL"
		})

		val configuration = createConfiguration {
			auth {
				userJID = "user@example.com".toBareJID()
				authenticationName = "user"
				password { "pencil" }
			}
		}.build()
		val context = SASLContext()

		// first client message
		assertEquals(
			"n,a=user@example.com,n=user,r=fyko+d2lbbFgONRv9qkxdawL",
			scram.evaluateChallenge(null, configuration, context)!!
				.fromBase64()
				.decodeToString(),
			"Invalid first client message"
		)

	}

	@Test
	fun test_messages_sha1() {
		val scram = SASLScramSHA1(randomGenerator = {
			"fyko+d2lbbFgONRv9qkxdawL"
		})

		val configuration = createConfiguration {
			auth {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
		}.build()
		val context = SASLContext()

		// first client message
		assertEquals(
			"n,,n=user,r=fyko+d2lbbFgONRv9qkxdawL",
			scram.evaluateChallenge(null, configuration, context)!!
				.fromBase64()
				.decodeToString(),
			"Invalid first client message"
		)

		// client last message
		assertEquals(
			"c=biws,r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j,p=v0X8v3Bz2T0CJGbJQyF0X+HI4Ts=", assertNotNull(
				scram.evaluateChallenge(
					"r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j,s=QSXCR+Q6sek8bf92,i=4096".encodeToByteArray().base64,
					configuration,
					context
				)
			).fromBase64()
				.decodeToString(), "Invalid last client message"
		)

		assertFalse(context.complete, "It should not be completed yet.")

		assertNull(
			scram.evaluateChallenge(
				"v=rmF9pqV8S7suAoZWja4dJRkFsKQ=".encodeToByteArray().base64, configuration, context
			)
		)
		assertTrue(context.complete, "It should be completed.")

	}

	@Test
	fun test_messages_sha256() {
		val scram = SASLScramSHA256(randomGenerator = {
			"rOprNGfwEbeRWgbNEkqO"
		})

		val configuration = createConfiguration {
			auth {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
		}.build()
		val context = SASLContext()

		// first client message
		assertEquals(
			"n,,n=user,r=rOprNGfwEbeRWgbNEkqO",
			scram.evaluateChallenge(null, configuration, context)!!
				.fromBase64()
				.decodeToString(),
			"Invalid first client message"
		)

		// client last message
		assertEquals(
			"c=biws,r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF\$k0,p=dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ=",
			assertNotNull(
				scram.evaluateChallenge(
					"r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF\$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096".encodeToByteArray().base64,
					configuration,
					context
				)
			).fromBase64()
				.decodeToString(),
			"Invalid last client message"
		)

		assertFalse(context.complete, "It should not be completed yet.")

		assertNull(
			scram.evaluateChallenge(
				"v=6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=".encodeToByteArray().base64, configuration, context
			)
		)
		assertTrue(context.complete, "It should be completed.")

	}

	@Test
	fun test_messages_sha256_invalid_server_signature() {
		val scram = SASLScramSHA256(randomGenerator = {
			"rOprNGfwEbeRWgbNEkqO"
		})

		val configuration = createConfiguration {
			auth {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
		}.build()
		val context = SASLContext()

		// first client message
		assertEquals(
			"n,,n=user,r=rOprNGfwEbeRWgbNEkqO",
			scram.evaluateChallenge(null, configuration, context)!!
				.fromBase64()
				.decodeToString(),
			"Invalid first client message"
		)

		// client last message
		assertEquals(
			"c=biws,r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF\$k0,p=dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ=",
			assertNotNull(
				scram.evaluateChallenge(
					"r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF\$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096".encodeToByteArray().base64,
					configuration,
					context
				)
			).fromBase64()
				.decodeToString(),
			"Invalid last client message"
		)

		assertFalse(context.complete, "It should not be completed yet.")

		assertFailsWith<ClientSaslException>("Fail expected. Given server response is invalid!") {
			scram.evaluateChallenge(
				"v=6rriTRBi23wpRR/wtup+mMhUZUn/dB5nLTJRsjl15G1=".encodeToByteArray().base64, configuration, context
			)
		}

		assertFalse(context.complete, "It should NOT be completed.")

	}
}