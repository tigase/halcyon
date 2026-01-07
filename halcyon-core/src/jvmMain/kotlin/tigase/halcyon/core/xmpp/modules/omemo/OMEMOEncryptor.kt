package tigase.halcyon.core.xmpp.modules.omemo

import korlibs.encoding.hex
import org.whispersystems.curve25519.NoSuchProviderException
import org.whispersystems.libsignal.*
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.fromBase64
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.toBase64
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.modules.mix.getMixAnnotation
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

/**
 * XEP-0454 helper.
 */
actual object OMEMOEncryptor {

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

        val ivSpec: AlgorithmParameterSpec = GCMParameterSpec(128, iv)
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

    data class DecryptedKey(val key: ByteArray, val isPreKey: Boolean) {}

    private fun retrieveKey(
        keyElements: List<Element>,
        senderAddr: SignalProtocolAddress,
        store: SignalProtocolStore,
        session: OMEMOSession,
        healSession: (SignalProtocolAddress)->Unit
    ): DecryptedKey? {
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
                    return DecryptedKey(sessionCipher.decrypt(msg), true)
                } catch (e: Exception) {
                    log.fine(e, { "failed to decrypt prekey ${keyElement.attributes["rid"]} from ${senderAddr}" })
                    if (iterator.hasNext()) {
                        continue
                    }
                    throw e;
                }
            } else {
                val msg = SignalMessage(encryptedKey)
                try {
                    return DecryptedKey(sessionCipher.decrypt(msg), false)
                } catch (e: Exception) {
                    log.fine(e, { "failed to decrypt key ${keyElement.attributes["rid"]} from ${senderAddr}" })
                    // should we try to heal sessions?
                    if (e is InvalidMessageException || e is NoSessionException || e is InvalidKeyIdException) {
                        // we need to try to recover
                        healSession(senderAddr);
                    }
                    if (iterator.hasNext()) {
                        continue
                    }
                    throw e;
                }
            }
        }
        return null
    }

    private fun findKeyElements(encElement: Element): List<Element> =
        encElement.getFirstChild("header")?.getChildren("key") ?: emptyList()
    
    actual fun decrypt(store: SignalProtocolStore, session: OMEMOSession, stanza: Message, healSession: (SignalProtocolAddress) -> Unit): OMEMOMessage {
        var hasCipherText = false
        try {
            val encElement =
                stanza.getChildrenNS("encrypted", OMEMOModule.XMLNS) ?: throw OMEMOException.NoEncryptedElement()
            val ciphertext = encElement.getFirstChild("payload")?.value?.fromBase64()?.also {
                hasCipherText = true
            }
            val senderId = encElement.getFirstChild("header")?.attributes?.get("sid")?.toInt()
                ?: throw OMEMOException.NoSidAttribute();

            val senderAddr = SignalProtocolAddress((stanza.getMixAnnotation()?.jid ?: stanza.attributes["from"]!!.toBareJID()).toString(), senderId)
            val iv = encElement.getFirstChild("header")?.getFirstChild("iv")?.value?.fromBase64()
                ?: throw OMEMOException.NoIV()
            // extracting inner key
            val decryptedKey = retrieveKey(findKeyElements(encElement), senderAddr, store, session, healSession)
                ?: throw OMEMOException.DeviceKeyNotFoundException();

            ciphertext?.let {
                val key = decryptedKey.key;
                if (key.size < 32) {
                    throw OMEMOException.InvalidKeyLengthException();
                }

                val authtaglength = key.size - 16
                val newCipherText = ByteArray(key.size - 16 + it.size)
                val newKey = ByteArray(16)

                System.arraycopy(it, 0, newCipherText, 0, it.size)
                System.arraycopy(key, 16, newCipherText, it.size, authtaglength)
                System.arraycopy(key, 0, newKey, 0, newKey.size)

                val keySpec = SecretKeySpec(newKey, ALGORITHM_NAME)
                val cipher = cipherInstance(Cipher.DECRYPT_MODE, keySpec, iv)
                val plain = cipher.doFinal(newCipherText)

                val decryptedBody = plain.toString(Charset.defaultCharset())
                stanza.replaceBody(decryptedBody)
            }

            return OMEMOMessage.Decrypted(stanza, senderAddr, store.getIdentity(senderAddr).publicKey.serialize().hex, decryptedKey.isPreKey)
        } catch (e: Throwable) {
            log.warning(e) { "Cannot decrypt message: ${stanza.getAsString()}" }
            val condition = when (e) {
                is OMEMOException -> e.condition
                is DuplicateMessageException -> OMEMOErrorCondition.DuplicateMessage
                else -> OMEMOErrorCondition.CannotDecrypt
            }
            if (condition == OMEMOErrorCondition.DuplicateMessage) {
                // if that is a message duplicate we should skip it to not enter/update message with this content as it was already processed.
                // removing body will force clients to not report/log it in the conversation
                stanza.getChildren("body").forEach { stanza.remove(it) }
            } else if (hasCipherText) {
                stanza.replaceBody(condition.message())
            }
            return OMEMOMessage.Error(stanza, condition)
        }
    }


    actual fun encrypt(session: OMEMOSession, plaintext: String?): Element {
        val iv = generateIV()
        val keyData = generateKey()
        val secretKey = SecretKeySpec(keyData, ALGORITHM_NAME)
        val (payload: ByteArray?, combinedKey: ByteArray) = plaintext?.let {
            val cipher: Cipher = cipherInstance(Cipher.ENCRYPT_MODE, secretKey, iv)
            var ciphertext = cipher.doFinal(plaintext.toByteArray())
            val authtagPlusInnerKey = ByteArray(16 + 16)
            val encData = ByteArray(ciphertext.size - 16)
            System.arraycopy(ciphertext, 0, encData, 0, encData.size)
            System.arraycopy(ciphertext, encData.size, authtagPlusInnerKey, 16, 16)
            System.arraycopy(keyData, 0, authtagPlusInnerKey, 0, keyData.size)
            Pair(encData, authtagPlusInnerKey)
        } ?: run {
            Pair(null, keyData)
        }

        return element("encrypted") {
            xmlns = OMEMOModule.XMLNS

            "header" {
                attributes["sid"] = "${session.localRegistrationId}"
                "iv" {
                    +iv.toBase64()
                }
                session.ciphers.map { (addr, sessionCipher) ->
                    try {
                        Pair(addr, sessionCipher.encrypt(combinedKey))
                    } catch (e: Throwable) {
                        log.warning { "failed to encrypt message for $addr, ${e.localizedMessage}" }
                        null;
                    }
                }.filterNotNull().forEach { (addr, m) ->
                    "key" {
                        attributes["rid"] = addr.deviceId.toString()

                        if (m.type == CiphertextMessage.PREKEY_TYPE) {
                            attributes["prekey"] = "true"
                        }

                        +m.serialize().toBase64()
                    }
                }

            }

            payload?.let { payload ->
                "payload" {
                    +payload.toBase64()
                }
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

actual typealias CiphertextMessage = org.whispersystems.libsignal.protocol.CiphertextMessage