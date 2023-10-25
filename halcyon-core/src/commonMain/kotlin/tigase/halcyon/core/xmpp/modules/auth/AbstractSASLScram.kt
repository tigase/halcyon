package tigase.halcyon.core.xmpp.modules.auth

import korlibs.crypto.*
import tigase.halcyon.core.Context
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

	SHA1, SHA256, SHA512
}

@Suppress("ArrayInDataClass")
data class SCRAMData(
	var authzId: String? = null,
	var authcId: String? = null,
	var authMessage: String? = null,
	var bindData: ByteArray = ByteArray(0),
	var bindType: BindType? = null,
//	var cb: String? = null,
	var gs2CBindFlag: String? = null,
	var gs2Header: String? = null,
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

	override fun isAllowedToUse(context: Context, config: Configuration, saslContext: SASLContext): Boolean =
		config.sasl is JIDPasswordSaslConfig

	private fun scramData(saslContext: SASLContext): SCRAMData {
		if (saslContext.mechanismData == null) {
			saslContext.mechanismData = SCRAMData()
		}
		return saslContext.mechanismData as SCRAMData
	}

	protected open fun prepareChannelBindingData(
		context: Context, config: Configuration, saslContext: SASLContext
	): Pair<BindType, ByteArray> {
		return Pair(BindType.N, ByteArray(0))
	}

	override fun evaluateChallenge(
		input: String?, context: Context, config: Configuration, saslContext: SASLContext
	): String? {
		val data = scramData(saslContext)
		val credentials = config.sasl as JIDPasswordSaslConfig

		if (data.stage == 0) {
			data.conce = randomGenerator.invoke()
			prepareChannelBindingData(context, config, saslContext).let { (bindType, bindData) ->
				data.bindType = bindType
				data.bindData = bindData
			}
			data.authcId = credentials.authcId ?: credentials.userJID.localpart!!
			data.authzId = if (credentials.authcId != null) {
				credentials.userJID.toString()
			} else null

			data.gs2CBindFlag = when (data.bindType!!) {
				BindType.N -> "n"
				BindType.Y -> "y"
				BindType.TlsUnique -> "p=tls-unique"
				BindType.TlsServerEndPoint -> "p=tls-server-end-point"
			}

			data.gs2Header = buildString {
				append(data.gs2CBindFlag)
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
			return "${data.gs2Header}${data.clientFirstMessageBare}".toBase64()
		} else if (data.stage == 1) {
			if (input == null) throw ClientSaslException("Unexpected empty input!")

			val serverFirstMessage = input.fromBase64().decodeToString()

			val r = serverFirstMessageRegex.matchEntire(serverFirstMessage)
				?: throw ClientSaslException("Bad challenge syntax")

			// val mext = r.groups[1]?.value
			val nonce = r.groups[2]?.value ?: throw ClientSaslException("Bad challenge syntax: missing nonce")
			val salt =
				r.groups[3]?.value?.fromBase64() ?: throw ClientSaslException("Bad challenge syntax: missing salt")
			val iterations =
				r.groups[4]?.value?.toInt() ?: throw ClientSaslException("Bad challenge syntax: missing iterations")

			if (!nonce.startsWith(data.conce!!)) throw ClientSaslException("Wrong nonce")

			val cBindInput = "${data.gs2Header}".encodeToByteArray() + data.bindData

			val clientFinalMessageBare = buildString {
				append("c=")
				append(cBindInput.toBase64())
				append(",")

				append("r=")
				append(nonce)
			}

			data.authMessage = data.clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageBare

			data.saltedPassword = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> PBKDF2.pbkdf2WithHmacSHA1(
					password = config.sasl.passwordCallback.invoke().encodeToByteArray(),
					salt = salt,
					iterationCount = iterations,
					160
				).bytes

				ScramHashAlgorithm.SHA256 -> PBKDF2.pbkdf2WithHmacSHA256(
					password = config.sasl.passwordCallback.invoke().encodeToByteArray(),
					salt = salt,
					iterationCount = iterations,
					256
				).bytes

				ScramHashAlgorithm.SHA512 -> PBKDF2.pbkdf2WithHmacSHA512(
					password = config.sasl.passwordCallback.invoke().encodeToByteArray(),
					salt = salt,
					iterationCount = iterations,
					512
				).bytes
			}

			val clientKey = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> HMAC.hmacSHA1(data.saltedPassword!!, clientKeyData)
				ScramHashAlgorithm.SHA256 -> HMAC.hmacSHA256(data.saltedPassword!!, clientKeyData)
				ScramHashAlgorithm.SHA512 -> HMAC.hmacSHA512(data.saltedPassword!!, clientKeyData)
			}.bytes
			val storedKey = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> clientKey.sha1()
				ScramHashAlgorithm.SHA256 -> clientKey.sha256()
				ScramHashAlgorithm.SHA512 -> clientKey.sha512()
			}.bytes
			val clientSignature = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> HMAC.hmacSHA1(key = storedKey, data.authMessage!!.encodeToByteArray())
				ScramHashAlgorithm.SHA256 -> HMAC.hmacSHA256(key = storedKey, data.authMessage!!.encodeToByteArray())
				ScramHashAlgorithm.SHA512 -> HMAC.hmacSHA512(key = storedKey, data.authMessage!!.encodeToByteArray())
			}.bytes

			val clientProof = clientKey.copyOf().also {
				for (i in it.indices) it[i] = it[i] xor clientSignature[i]
			}

			val clientFinalMessageStep2 = buildString {
				append(clientFinalMessageBare)
				append(",")
				append("p=")
				append(clientProof.toBase64())
			}
			++data.stage
			return clientFinalMessageStep2.toBase64()
		} else if (data.stage == 2) {
			if (input == null) throw ClientSaslException("Unexpected empty input!")

			val r = serverLastMessageRegex.matchEntire(
				input.fromBase64().decodeToString()
			) ?: throw ClientSaslException("Bad challenge syntax")

			r.groups[1]?.let {
				throw ClientSaslException("Error: $it")
			}
			val v = r.groups[2]?.value ?: throw ClientSaslException("Bad challenge syntax")

			val serverKey = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> HMAC.hmacSHA1(data.saltedPassword!!, serverKeyData)
				ScramHashAlgorithm.SHA256 -> HMAC.hmacSHA256(data.saltedPassword!!, serverKeyData)
				ScramHashAlgorithm.SHA512 -> HMAC.hmacSHA512(data.saltedPassword!!, serverKeyData)
			}.bytes
			val serverSignature = when (hashAlgorithm) {
				ScramHashAlgorithm.SHA1 -> HMAC.hmacSHA1(key = serverKey, data.authMessage!!.encodeToByteArray())
				ScramHashAlgorithm.SHA256 -> HMAC.hmacSHA256(key = serverKey, data.authMessage!!.encodeToByteArray())
				ScramHashAlgorithm.SHA512 -> HMAC.hmacSHA512(key = serverKey, data.authMessage!!.encodeToByteArray())
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

fun randomString(len: Int = 22): String {
	val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=_-"
	var result = ""
	for (i in 0..len) {
		result += alphabet[Random.nextInt(alphabet.length)]
	}
	return result
}