package tigase.halcyon.core.xmpp.modules.auth

import korlibs.crypto.HMAC
import korlibs.crypto.PBKDF2
import korlibs.crypto.sha1
import korlibs.crypto.sha256
import tigase.halcyon.core.configuration.Configuration
import tigase.halcyon.core.configuration.JIDPasswordSaslConfig
import tigase.halcyon.core.fromBase64
import tigase.halcyon.core.toBase64
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
	var authzId: String? = null,
	var authcId: String? = null,
	var authMessage: String? = null,
	var bindData: ByteArray = ByteArray(0),
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
	private val clientKeyData: ByteArray = "Client Key".encodeToByteArray(),
	private val serverKeyData: ByteArray = "Server Key".encodeToByteArray(),
) : SASLMechanism {

	private val serverFirstMessageRegex = Regex(
		"^(m=[^,]+,)?r=([^,]+),s=([^,]+),i=([0-9]+)(?:,.*)?$", RegexOption.IGNORE_CASE
	)
	private val serverLastMessageRegex = Regex(
		"^(?:e=([^,]+)|v=([^,]+)(?:,.*)?)$", RegexOption.IGNORE_CASE
	)

	override fun isAllowedToUse(config: Configuration, saslContext: SASLContext): Boolean =
		config.sasl is JIDPasswordSaslConfig

	private fun scramData(saslContext: SASLContext): SCRAMData {
		if (saslContext.mechanismData == null) {
			saslContext.mechanismData = SCRAMData()
		}
		return saslContext.mechanismData as SCRAMData
	}

	override fun evaluateChallenge(input: String?, config: Configuration, saslContext: SASLContext): String? {
		val data = scramData(saslContext)
		val credentials = config.sasl as JIDPasswordSaslConfig

		if (data.stage == 0) {
			data.conce = randomGenerator.invoke()
			data.bindType = BindType.N // TODO Implement Support for binding
			data.bindData = ByteArray(0)
			data.authcId = credentials.authcId ?: credentials.userJID.localpart!!
			data.authzId = if (credentials.authcId != null) {
				credentials.userJID.toString()
			} else null


			data.cb = buildString {
				when (data.bindType!!) {
					BindType.N -> append("n")
					BindType.Y -> append("y")
					BindType.TlsUnique -> append("p=tls-unique")
					BindType.TlsServerEndPoint -> append("p=tls-server-end-point")
				}
				append(",")

				data.authzId?.let {
					append("a=").append(it)
				}
				append(",")
			}



			data.clientFirstMessageBare = buildString {
				append("n=${data.authcId},")
				append("r=${data.conce}")
			}

			++data.stage
			return "${data.cb}${data.clientFirstMessageBare}".toBase64()
		} else if (data.stage == 1) {
			if (input == null) throw ClientSaslException("Unexpected empty input!")

			val serverFirstMessage = input.fromBase64()
				.decodeToString()

			val r = serverFirstMessageRegex.matchEntire(serverFirstMessage)
				?: throw ClientSaslException("Bad challenge syntax")

			// val mext = r.groups[1]?.value
			val nonce = r.groups[2]?.value ?: throw ClientSaslException("Bad challenge syntax: missing nonce")
			val salt =
				r.groups[3]?.value?.fromBase64() ?: throw ClientSaslException("Bad challenge syntax: missing salt")
			val iterations =
				r.groups[4]?.value?.toInt() ?: throw ClientSaslException("Bad challenge syntax: missing iterations")

			if (!nonce.startsWith(data.conce!!)) throw ClientSaslException("Wrong nonce")

			val clientFinalMessageStep1 = buildString {
				append("c=")
				append((data.cb!!.encodeToByteArray() + data.bindData).toBase64())
				append(",")

				append("r=")
				append(nonce)
			}

			data.authMessage = data.clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageStep1

			data.saltedPassword = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> PBKDF2.pbkdf2WithHmacSHA1(
					password = config.sasl.passwordCallback.invoke()
						.encodeToByteArray(), salt = salt, iterationCount = iterations, 160
				).bytes

				ScramHashAlgorithm.SHA256 -> PBKDF2.pbkdf2WithHmacSHA256(
					password = config.sasl.passwordCallback.invoke()
						.encodeToByteArray(), salt = salt, iterationCount = iterations, 256
				).bytes
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
				append(clientProof.toBase64())
			}
			++data.stage
			return clientFinalMessageStep2.toBase64()
		} else if (data.stage == 2) {
			if (input == null) throw ClientSaslException("Unexpected empty input!")

			val r = serverLastMessageRegex.matchEntire(
				input.fromBase64()
					.decodeToString()
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

			if (!(serverSignature contentEquals v.fromBase64())) {
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