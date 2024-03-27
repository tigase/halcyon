package tigase.halcyon.core.xmpp.modules.omemo

import korlibs.crypto.encoding.hexLower
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.io.CipherInputStream
import org.bouncycastle.crypto.io.CipherOutputStream
import org.bouncycastle.crypto.modes.AEADBlockCipher
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom


object OMEMOFileEncryptor {

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.omemo.OMEMOFileEncryptor")

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

//    fun cipherOutputStream(keyAndIv: ByteArray, output: OutputStream): OutputStream {
//        val iv = keyAndIv.copyOfRange(0, 12)
//        val keyData = keyAndIv.copyOfRange(12, 12 + (KEY_SIZE / 8))
//        val secretKey = SecretKeySpec(keyData, ALGORITHM_NAME)
//        val cipher = cipherInstance(Cipher.ENCRYPT_MODE, secretKey, iv)
//        return CipherOutputStream(output, cipher)
//    }
//
//    fun cipherInputStream(keyAndIv: ByteArray, input: InputStream): InputStream {
//        val iv = keyAndIv.copyOfRange(0, 12)
//        val keyData = keyAndIv.copyOfRange(12, 12 + (KEY_SIZE / 8))
//        val secretKey = SecretKeySpec(keyData, ALGORITHM_NAME)
//        val cipher = cipherInstance(Cipher.DECRYPT_MODE, secretKey, iv)
//        return CipherInputStream(input, cipher)
//    }

    fun cipherInputStream(keyAndIv: ByteArray, input: InputStream): InputStream {
        val iv = getIv(keyAndIv)
        val keyData = getKey(keyAndIv)
        val cipher: AEADBlockCipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(false, AEADParameters(KeyParameter(keyData), 128, iv))

        return CipherInputStream(input, cipher)
    }

    fun cipherOutputStream(keyAndIv: ByteArray, output: OutputStream): OutputStream {
        val iv = getIv(keyAndIv)
        val keyData = getKey(keyAndIv)
        val cipher: AEADBlockCipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(true, AEADParameters(KeyParameter(keyData), 128, iv))

        return CipherOutputStream(output, cipher)
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