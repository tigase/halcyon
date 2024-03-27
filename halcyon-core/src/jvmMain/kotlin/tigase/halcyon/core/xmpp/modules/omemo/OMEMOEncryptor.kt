package tigase.halcyon.core.xmpp.modules.omemo

import korlibs.crypto.encoding.hex
import org.whispersystems.libsignal.*
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.SignalProtocolStore
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.fromBase64
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.toBase64
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.toBareJID
import java.nio.charset.Charset
import java.security.*
import java.security.InvalidKeyException
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object OMEMOEncryptor {

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.omemo.OMEMOEncryptor")

   private const val CIPHER_NAME: String = "AES/GCM/NoPadding"
   private const val ALGORITHM_NAME: String = "AES"
   private const val KEY_SIZE = 128

    private val rnd = SecureRandom()

    @Throws(NoSuchAlgorithmException::class)
     fun generateKey(): ByteArray {
        val generator = KeyGenerator.getInstance(ALGORITHM_NAME)
        generator.init(KEY_SIZE)
        return generator.generateKey().encoded
    }

     fun generateIV(): ByteArray {
        val iv = ByteArray(12)
        rnd.nextBytes(iv)
        return iv
    }

    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        NoSuchProviderException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class
    )
    private fun cipherInstance(mode: Int, secretKey: SecretKeySpec, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(CIPHER_NAME)

        val ivSpec: AlgorithmParameterSpec = GCMParameterSpec(KEY_SIZE, iv)
        cipher.init(mode, secretKey, ivSpec)
        return cipher
    }

    private fun checkPotentialKey(
        senderAddr: SignalProtocolAddress,
        store: SignalProtocolStore,
        session: OMEMOSession,
        keyElement: Element
    ): ByteArray? {
        try {
            if (keyElement.attributes["rid"]?.toInt() != session.localRegistrationId) return null

            val preKey = keyElement.attributes["prekey"] in listOf("1", "true")
            val encryptedKey = keyElement.value?.fromBase64() ?: throw HalcyonException("Cannot decode key")

            val sessionCipher =
                session.ciphers[senderAddr] ?: let {
                    SessionCipher(store, senderAddr)
                }
            val key = if (preKey) {
                sessionCipher.decrypt(PreKeySignalMessage(encryptedKey))
            } else {
                sessionCipher.decrypt(SignalMessage(encryptedKey))
            }
            session.ciphers[senderAddr] = sessionCipher
            return key
        } catch (e: InvalidVersionException) {
            return null
        } catch (e: InvalidKeyException) {
            return null
        } catch (e: LegacyMessageException) {
            return null
        } catch (e: InvalidMessageException) {
            return null
        } catch (e: DuplicateMessageException) {
            return null
        } catch (e: InvalidKeyIdException) {
            return null
        } catch (e: UntrustedIdentityException) {
            return null
        }
    }

    private fun retrieveKey(
        keyElements: List<Element>,
        senderAddr: SignalProtocolAddress,
        store: SignalProtocolStore,
        session: OMEMOSession
    ): ByteArray? {
        val iterator = keyElements.filter { it.attributes["rid"]?.toInt() == session.localRegistrationId }.iterator()
        val sessionCipher =
            session.ciphers[senderAddr] ?: let {
                SessionCipher(store, senderAddr)
            }
        while (iterator.hasNext()) {
            val keyElement = iterator.next()
            val preKey = keyElement.attributes["prekey"] in listOf("1", "true")
            val encryptedKey = keyElement.value?.fromBase64() ?: continue

            if (preKey) {
                val msg = PreKeySignalMessage(encryptedKey)
                try {
                    return sessionCipher.decrypt(msg)
                } catch (e: Exception) {
                    if (iterator.hasNext()) {
                        continue
                    }
                    throw OMEMOException("Cannot decrypt OMEMO message.", e)
                }
            } else {
                val msg = SignalMessage(encryptedKey)
                try {
                    return sessionCipher.decrypt(msg)
                } catch (e: Exception) {
                    if (iterator.hasNext()) {
                        continue
                    }
                    throw OMEMOException("Cannot decrypt OMEMO message.", e)
                }
            }
        }
        return null
    }

    private fun findKeyElements(encElement: Element): List<Element> =
        encElement.getFirstChild("header")?.getChildren("key") ?: emptyList()


    fun decrypt(store: SignalProtocolStore, session: OMEMOSession, stanza: Message): Message {
        try {
            val myAddr = SignalProtocolAddress(session.localJid.toString(), session.localRegistrationId)
            val encElement =
                stanza.getChildrenNS("encrypted", OMEMOModule.XMLNS) ?: throw HalcyonException("No enc element")
            val senderId = encElement.getFirstChild("header")?.attributes?.get("sid")?.toInt()
                ?: throw HalcyonException("No sid attribute element")
            val senderAddr = SignalProtocolAddress(stanza.attributes["from"]!!.toBareJID().toString(), senderId)
            val iv = encElement.getFirstChild("header")?.getFirstChild("iv")?.value?.fromBase64()
                ?: throw HalcyonException("No IV element")
            var ciphertext = encElement.getFirstChild("payload")?.value?.fromBase64() ?: ByteArray(0)

            // extracting inner key
            var key = try {
                retrieveKey(findKeyElements(encElement), senderAddr, store, session)
            } catch (e: OMEMOException) {
                log.warning(e) { "Cannot retrieve key" }
                stanza.replaceBody("Cannot decrypt message.")
                return OMEMOMessage.Error(stanza, OMEMOErrorCondition.CannotDecrypt)
            }

            if (key == null) {
                stanza.replaceBody("Message is not encrypted for this device.")
                return OMEMOMessage.Error(stanza, OMEMOErrorCondition.DeviceKeyNotFound)
            }

            if (key.size >= 32) {
                val authtaglength = key.size - 16
                val newCipherText = ByteArray(key.size - 16 + ciphertext.size)
                val newKey = ByteArray(16)

                System.arraycopy(ciphertext, 0, newCipherText, 0, ciphertext.size)
                System.arraycopy(key, 16, newCipherText, ciphertext.size, authtaglength)
                System.arraycopy(key, 0, newKey, 0, newKey.size)

                ciphertext = newCipherText
                key = newKey
            }

            val keySpec = SecretKeySpec(key, ALGORITHM_NAME)
            val cipher = cipherInstance(Cipher.DECRYPT_MODE, keySpec, iv)
            val plain = cipher.doFinal(ciphertext)

            val decryptedBody = plain.toString(Charset.defaultCharset())
            stanza.replaceBody(decryptedBody)

            return OMEMOMessage.Decrypted(stanza, senderAddr, store.getIdentity(senderAddr).publicKey.serialize().hex)
        } catch (e: Exception) {
            log.warning(e) { "Cannot decrypt message" }
            stanza.replaceBody("Cannot decrypt message.")
            return OMEMOMessage.Error(stanza, OMEMOErrorCondition.CannotDecrypt)
        }
    }


    fun encrypt(session: OMEMOSession, plaintext: String): Element {
        val iv = generateIV()
        val keyData = generateKey()
        val secretKey = SecretKeySpec(keyData, ALGORITHM_NAME)
        val cipher: Cipher = cipherInstance(Cipher.ENCRYPT_MODE, secretKey, iv)

        var ciphertext = cipher.doFinal(plaintext.toByteArray())

        val authtagPlusInnerKey = ByteArray(16 + 16)
        val encData = ByteArray(ciphertext.size - 16)
        System.arraycopy(ciphertext, 0, encData, 0, encData.size)
        System.arraycopy(ciphertext, encData.size, authtagPlusInnerKey, 16, 16)
        System.arraycopy(keyData, 0, authtagPlusInnerKey, 0, keyData.size)
        ciphertext = encData


        return element("encrypted") {
            xmlns = OMEMOModule.XMLNS

            "header" {
                attributes["sid"] = "${session.localRegistrationId}"
                "iv" {
                    +iv.toBase64()
                }
                session.ciphers.forEach { addr, sessionCipher ->
                    "key" {
                        attributes["rid"] = addr.deviceId.toString()

                        val m = sessionCipher.encrypt(authtagPlusInnerKey)
                        if (m.type == CiphertextMessage.PREKEY_TYPE) {
                            attributes["prekey"] = "true"
                        }

                        +m.serialize().toBase64()
                    }
                }

            }

            "payload" {
                +ciphertext.toBase64()
            }
        }
    }

}

private fun Message.replaceBody(newBody: String) {
    this.getChildren("body").forEach { this.remove(it) }
    this.add(element("body") {
        +newBody
    })
}