package tigase.halcyon.core.xmpp.modules.auth

import com.soywiz.krypto.HMAC
import com.soywiz.krypto.PBKDF2
import com.soywiz.krypto.sha1
import com.soywiz.krypto.sha256
import tigase.halcyon.core.Base64
import tigase.halcyon.core.configuration.Configuration
import kotlin.experimental.xor
import kotlin.random.Random

enum class BindType {

	/**
	 * Client doesn't support channel binding.
	 */
	N,

	/**
	 * Client does support channel binding but thinks the server does not.
	 */
	Y,

	/**
	 * Client requires channel binding: <code>tls-unique</code>.
	 */
	TlsUnique,

	/**
	 * Client requires channel binding: <code>tls-server-end-point</code>.
	 */
	TlsServerEndPoint
}

enum class ScramHashAlgorithm {

	SHA1,
	SHA256
}

@Suppress("ArrayInDataClass")
data class SCRAMData(
	var authMessage: String? = null,
	var bindData: ByteArray? = null,
	var bindType: BindType? = null,
	var cb: String? = null,
	var clientFirstMessageBare: String? = null,
	var conce: String? = null,
	var saltedPassword: ByteArray? = null,
	var stage: Int = 0,
) : MechanismData

abstract class AbstractSASLScram(
	override val name: String,
	val hashAlgorithm: ScramHashAlgorithm,
	private val randomGenerator: () -> String,
	private val clientKeyData: ByteArray, private val serverKeyData: ByteArray,
) : SASLMechanism {

	private val serverFirstMessageRegex = Regex(
		"^(m=[^\\000=]+,)?r=([\\x21-\\x2B\\x2D-\\x7E]+),s=([a-zA-Z0-9/+=]+),i=(\\d+)(?:,.*)?$", RegexOption.IGNORE_CASE
	)
	private val serverLastMessageRegex = Regex(
		"^(?:e=([^,]+)|v=([a-zA-Z0-9/+=]+)(?:,.*)?)$", RegexOption.IGNORE_CASE
	)

	override fun isAllowedToUse(config: Configuration, saslContext: SASLContext): Boolean =
		config.userJID != null && config.passwordCallback != null

	private fun scramData(saslContext: SASLContext): SCRAMData {
		if (saslContext.mechanismData == null) {
			saslContext.mechanismData = SCRAMData()
		}
		return saslContext.mechanismData as SCRAMData
	}

	override fun evaluateChallenge(input: String?, config: Configuration, saslContext: SASLContext): String? {
		val data = scramData(saslContext)

		if (data.stage == 0) {
			data.conce = randomGenerator.invoke()
			data.bindType = BindType.N // TODO Implement Support for binding
			data.bindData = null

			data.cb = buildString {
				when (data.bindType!!) {
					BindType.N -> append("n")
					BindType.Y -> append("y")
					BindType.TlsUnique -> append("p=tls-unique")
					BindType.TlsServerEndPoint -> append("p=tls-server-end-point")
				}
				append(",")

//		TODO Implement support for AuthzId
//		 append("a=").append(config.userJID)
//				append(config.userJID!!.localpart)
				append(",")
			}

			data.clientFirstMessageBare = buildString {
				append("n=${config.userJID!!.localpart},")
				append("r=${data.conce}")
			}

			++data.stage
			return Base64.encode("${data.cb}${data.clientFirstMessageBare}")
		} else if (data.stage == 1) {
			if (input == null) throw ClientSaslException("Unexpected empty input!")

			val serverFirstMessage = Base64.decode(input)
				.concatToString()

			val r = serverFirstMessageRegex.matchEntire(serverFirstMessage)
				?: throw ClientSaslException("Bad challenge syntax")

			// val mext = r.groups[1]?.value
			val nonce = r.groups[2]?.value ?: throw ClientSaslException("Bad challenge syntax: missing nonce")
			val salt = r.groups[3]?.value?.let { Base64.decodeToByteArray(it) }
				?: throw ClientSaslException("Bad challenge syntax: missing salt")
			val iterations =
				r.groups[4]?.value?.toInt() ?: throw ClientSaslException("Bad challenge syntax: missing iterations")

			if (!nonce.startsWith(data.conce!!)) throw ClientSaslException("Wrong nonce")

			val bindData = ByteArray(0)

			val clientFinalMessageStep1 = buildString {
				append("c=")
				append(Base64.encode(data.cb!!.encodeToByteArray() + bindData))
				append(",")

				append("r=")
				append(nonce)
			}

			data.authMessage = data.clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageStep1

			data.saltedPassword = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> PBKDF2.pbkdf2WithHmacSHA1(
					password = config.passwordCallback!!.getPassword()
						.encodeToByteArray(), salt = salt, iterationCount = iterations, 160
				)

				ScramHashAlgorithm.SHA256 -> PBKDF2.pbkdf2WithHmacSHA256(
					password = config.passwordCallback!!.getPassword()
						.encodeToByteArray(), salt = salt, iterationCount = iterations, 256
				)
			}

			val clientKey = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> HMAC.hmacSHA1(data.saltedPassword!!, clientKeyData)
				ScramHashAlgorithm.SHA256 -> HMAC.hmacSHA256(data.saltedPassword!!, clientKeyData)
			}.bytes
			val storedKey = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> clientKey.sha1()
				ScramHashAlgorithm.SHA256 -> clientKey.sha256()
			}.bytes
			val clientSignature = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> HMAC.hmacSHA1(key = storedKey, data.authMessage!!.encodeToByteArray())
				ScramHashAlgorithm.SHA256 -> HMAC.hmacSHA256(key = storedKey, data.authMessage!!.encodeToByteArray())
			}.bytes

			val clientProof = clientKey.copyOf()
				.also {
					for (i in it.indices) it[i] = it[i] xor clientSignature[i]
				}

			val clientFinalMessageStep2 = buildString {
				append(clientFinalMessageStep1)
				append(",")
				append("p=")
				append(Base64.encode(clientProof))
			}
			++data.stage
			return Base64.encode(clientFinalMessageStep2)
		} else if (data.stage == 2) {
			if (input == null) throw ClientSaslException("Unexpected empty input!")

			val r = serverLastMessageRegex.matchEntire(
				Base64.decode(input)
					.concatToString()
			) ?: throw ClientSaslException("Bad challenge syntax")

			r.groups[1]?.let {
				throw ClientSaslException("Error: $it")
			}
			val v = r.groups[2]?.value ?: throw ClientSaslException("Bad challenge syntax")

			val serverKey = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> HMAC.hmacSHA1(data.saltedPassword!!, serverKeyData)
				ScramHashAlgorithm.SHA256 -> HMAC.hmacSHA256(data.saltedPassword!!, serverKeyData)
			}.bytes
			val serverSignature = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> HMAC.hmacSHA1(key = serverKey, data.authMessage!!.encodeToByteArray())
				ScramHashAlgorithm.SHA256 -> HMAC.hmacSHA256(key = serverKey, data.authMessage!!.encodeToByteArray())
			}.bytes

			if (!(serverSignature contentEquals Base64.decodeToByteArray(v))) {
				throw ClientSaslException("Invalid Server Signature")
			}
			++data.stage
			saslContext.complete = true
			return null
		} else if (saslContext.complete && input == null) {
			return null
		} else {
			throw IllegalStateException("SASL Client in illegal state. stage=${data.stage} complete=${saslContext.complete}")
		}
	}

}

fun hi(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
	val z = salt + byteArrayOf(0, 0, 0, 1)
	var u = HMAC.hmacSHA256(password, z).bytes
	val result = u.copyOf()
	for (i in 1 until iterations) {
		u = HMAC.hmacSHA256(password, u).bytes
		for (j in result.indices) result[j] = result[j] xor u[j]
	}
	return result
}

fun randomString(len: Int = 20): String {
	val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
	var result = ""
	for (i in 0..len) {
		result += alphabet[Random.nextInt(alphabet.length)]
	}
	return result
}