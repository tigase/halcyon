package tigase.halcyon.core.xmpp.modules.omemo

import korlibs.crypto.encoding.hexLower
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

actual typealias InputStream = java.io.InputStream
actual typealias OutputStream = java.io.OutputStream

actual object OMEMOFileEncryptor {

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

    actual fun cipherOutputStream(keyAndIv: ByteArray, output: OutputStream): OutputStream {
        val iv = getIv(keyAndIv)
        val keyData = getKey(keyAndIv)
        val secretKey = SecretKeySpec(keyData, ALGORITHM_NAME)
        val cipher = Cipher.getInstance(CIPHER_NAME).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        }
        return CipherOutputStream(output, cipher)
    }

    actual fun cipherInputStream(keyAndIv: ByteArray, input: InputStream): InputStream {
        val iv = getIv(keyAndIv)
        val keyData = getKey(keyAndIv)
        val secretKey = SecretKeySpec(keyData, ALGORITHM_NAME)
        val cipher = Cipher.getInstance(CIPHER_NAME).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        }
        return CipherInputStream(input, cipher)
    }

//    fun cipherInputStream(keyAndIv: ByteArray, input: InputStream): InputStream {
//        val iv = getIv(keyAndIv)
//        val keyData = getKey(keyAndIv)
//        val cipher: AEADBlockCipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
//        cipher.init(false, AEADParameters(KeyParameter(keyData), 128, iv))
//
//        return CipherInputStream(input, cipher)
//    }
//
//    fun cipherOutputStream(keyAndIv: ByteArray, output: OutputStream): OutputStream {
//        val iv = getIv(keyAndIv)
//        val keyData = getKey(keyAndIv)
//        val cipher: AEADBlockCipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
//        cipher.init(true, AEADParameters(KeyParameter(keyData), 128, iv))
//
//        return CipherOutputStream(output, cipher)
//    }

    actual fun encrypt(input: InputStream, keyAndIv: ByteArray, output: OutputStream) {
        cipherOutputStream(keyAndIv, output).use {
            input.transferTo(it)
        }
    }

    actual fun decrypt(input: InputStream, keyAndIv: ByteArray, output: OutputStream) {
        cipherInputStream(keyAndIv, input).use {
            it.transferTo(output)
        }
    }

}