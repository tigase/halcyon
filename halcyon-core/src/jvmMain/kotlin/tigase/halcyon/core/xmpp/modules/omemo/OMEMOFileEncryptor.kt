package tigase.halcyon.core.xmpp.modules.omemo

import korlibs.encoding.hexLower
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
//import java.io.InputStream
//import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

typealias InputStream = java.io.InputStream
typealias OutputStream = java.io.OutputStream

object OMEMOFileEncryptor {

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.omemo.OMEMOFileEncryptor")

    private const val CIPHER_NAME: String = "AES/GCM/NoPadding"
    private const val ALGORITHM_NAME: String = "AES"

    private val rnd = SecureRandom()

    /**
     * Generates IV and encryption key.
     */
    fun generateIvAndKey(): String {
        return ByteArray(12 + 32).apply {
            rnd.nextBytes(this)
        }.hexLower
    }

    internal fun getIv(data: ByteArray): ByteArray {
        if (data.size > 32)
            return data.copyOfRange(0, data.size - 32)
        else throw HalcyonException("Key is too short.")
    }

    internal fun getKey(data: ByteArray): ByteArray {
        if (data.size > 32)
            return data.copyOfRange(data.size - 32, data.size)
        else throw HalcyonException("Key is too short.")
    }

    fun cipherOutputStream(keyAndIv: ByteArray, output: OutputStream): OutputStream {
        val iv = getIv(keyAndIv)
        val keyData = getKey(keyAndIv)
        val secretKey = SecretKeySpec(keyData, ALGORITHM_NAME)
        val cipher = Cipher.getInstance(CIPHER_NAME).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        }
        return CipherOutputStream(output, cipher)
    }

    fun cipherInputStream(keyAndIv: ByteArray, input: InputStream): InputStream {
        val iv = getIv(keyAndIv)
        val keyData = getKey(keyAndIv)
        val secretKey = SecretKeySpec(keyData, ALGORITHM_NAME)
        val cipher = Cipher.getInstance(CIPHER_NAME).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        }
        return CipherInputStream(input, cipher)
    }

    fun encrypt(input: InputStream, keyAndIv: ByteArray, output: OutputStream) {
        cipherOutputStream(keyAndIv, output).use {
            input.transferTo(it)
        }
    }

    fun decrypt(input: InputStream, keyAndIv: ByteArray, output: OutputStream) {
        cipherInputStream(keyAndIv, input).use {
            it.transferTo(output)
        }
    }

}