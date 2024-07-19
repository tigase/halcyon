package tigase.halcyon.core.xmpp.modules.omemo

import OpenSSL.*
import korlibs.crypto.encoding.hex
import kotlinx.cinterop.*
import platform.Foundation.NSInputStream
import platform.Foundation.NSOutputStream
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.fromBase64
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.toBase64
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.toBareJID

actual object OMEMOEncryptor {
    
    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.omemo.OMEMOEncryptor")
    private val engine = AesGcmEngine();
    
    private fun retrieveKey(
        keyElements: List<Element>,
        senderAddr: SignalProtocolAddress,
        store: SignalProtocolStore,
        session: OMEMOSession
    ): ByteArray? {
        val localKeys = keyElements.filter { it.attributes["rid"]?.toInt() == session.localRegistrationId };
        val iterator = localKeys.iterator()
        val sessionCipher =
            session.ciphers[senderAddr] ?: let {
                SessionCipher(store, senderAddr)
            }
        while (iterator.hasNext()) {
            val keyElement = iterator.next()
            val preKey = keyElement.attributes["prekey"] in listOf("1", "true")
            val encryptedKey = keyElement.value?.fromBase64() ?: continue

            
            try {
                return sessionCipher.decrypt(data = encryptedKey, isPreKey = preKey);
            } catch (e: Exception) {
                log.warning(e, { "failed to decrypt key for " + sessionCipher.address + ", store = " + sessionCipher.store + ", error: " + e.cause })
                e.printStackTrace();
                if (iterator.hasNext()) {
                    continue
                }
                throw OMEMOException("Cannot decrypt OMEMO message.", e)
            }
        }
        return null
    }

    private fun findKeyElements(encElement: Element): List<Element> =
        encElement.getFirstChild("header")?.getChildren("key") ?: emptyList()

    actual fun decrypt(
        store: SignalProtocolStore,
        session: OMEMOSession,
        stanza: Message
    ): Message {
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
                val newCipherText = ciphertext.copyOf().plus(key.copyOfRange(16, 16 + authtaglength));
                val newKey = key.copyOfRange(0, 16);

                ciphertext = newCipherText
                key = newKey
            }

            val result = engine.decrypt(iv, key, ciphertext, null);

            val decryptedBody = (result?.decodeToString()) ?: "Cannot decrypt message.";
            stanza.replaceBody(decryptedBody)

            return OMEMOMessage.Decrypted(stanza, senderAddr, store.getIdentity(senderAddr)!!.publicKey.serialize().hex)
        } catch (e: Exception) {
            log.warning(e) { "Cannot decrypt message" }
            stanza.replaceBody("Cannot decrypt message.")
            return OMEMOMessage.Error(stanza, OMEMOErrorCondition.CannotDecrypt)
        }
    }
    
    @OptIn(ExperimentalForeignApi::class)
    fun generateIV(): ByteArray {
        val data = ByteArray(12);
        SecRandomCopyBytes(kSecRandomDefault, 12.toULong(), data.toCValues());
        return data;
    }
    
    @OptIn(ExperimentalForeignApi::class)
    fun generateKey(keySize: Int = 128): ByteArray {
        val keySizeInBytes = keySize / 8;
        val data = ByteArray(keySizeInBytes);
        SecRandomCopyBytes(kSecRandomDefault, keySizeInBytes.toULong(), data.toCValues());
        return data;
    }

    actual fun encrypt(
        session: OMEMOSession,
        plaintext: String
    ): Element {
        log.finest("encrypting message started...");
        log.finest("generating IV...")
        val iv = generateIV()
        log.finest("generating key...")
        val keyData = generateKey()

        log.finest("encrypting with AES...")
        val encrypted = engine.encrypt(iv, keyData, plaintext.encodeToByteArray());
        log.finest("encrypted with AES and got " + encrypted.data.size + " bytes")
        val authtagPlusInnerKey = keyData.plus(encrypted.tag);

        return element("encrypted") {
            xmlns = OMEMOModule.XMLNS

            "header" {
                attributes["sid"] = "${session.localRegistrationId}"
                "iv" {
                    +iv.toBase64()
                }
                session.ciphers.forEach { (addr, sessionCipher) ->
                    log.finest("adding encryption key for " + addr.deviceId)
                    "key" {
                        attributes["rid"] = addr.deviceId.toString()
                        
                        val m = sessionCipher.encrypt(authtagPlusInnerKey)
                        if (m.isPreKey) {
                            attributes["prekey"] = "true"
                        }
                        
                        +m.data.toBase64()
                    }
                }
            }
            
            "payload" {
                +encrypted.data.toBase64()
            }
        }
    }

}

class AesGcmCipher(val iv: ByteArray, val key: ByteArray) {
    val engine = AesGcmEngine();
}

@OptIn(ExperimentalForeignApi::class)
class AesGcmEngine {

    fun decrypt(iv: ByteArray, key: ByteArray, payload: ByteArray, tag: ByteArray?): ByteArray? {
        val ctx = EVP_CIPHER_CTX_new();
        val cipher = if (key.size == 32) { EVP_aes_256_gcm() } else { EVP_aes_128_gcm() };
        EVP_DecryptInit_ex(ctx, cipher, null, null, null);
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, iv.size, null);
        EVP_DecryptInit_ex(ctx, null, null, key.toUByteArray().toCValues(), iv.toUByteArray().toCValues());
        EVP_CIPHER_CTX_set_padding(ctx, 1);

        var auth = tag ?: payload.copyOfRange(payload.size - 16, payload.size);
        var encoded = if (tag != null) { payload } else { payload.copyOfRange(0, payload.size - 16) }

        val decrypted = memScoped {
            val output = allocArray<UByteVar>(encoded.size);
            val outputLen: IntVar = alloc<IntVar>();
            EVP_DecryptUpdate(ctx, output, outputLen.ptr, encoded.toUByteArray().toCValues(), encoded.size);
            
            val result = output.readBytes(outputLen.value); //output.copyOfRange(0, outputLen.value).toByteArray();
            EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_CCM_SET_TAG, auth.size, auth.toCValues());

            val ret = EVP_DecryptFinal_ex(ctx, output, outputLen.ptr);
            return@memScoped if (ret >= 0) { result } else { null }
        }

        EVP_CIPHER_CTX_free(ctx);
        return decrypted;
    }

    sealed class DecryptionStep {
        class InputChunk(val data: ByteArray): DecryptionStep()
        class EndOfInput(val authTag: ByteArray?): DecryptionStep()
    }

    fun decrypt(iv: ByteArray, key: ByteArray, chunkProvider: () -> DecryptionStep, chunkConsumer: (UByteArray) -> Unit) {
        val ctx = EVP_CIPHER_CTX_new();
        val cipher = if (key.size == 32) { EVP_aes_256_gcm() } else { EVP_aes_128_gcm() };
        EVP_DecryptInit_ex(ctx, cipher, null, null, null);
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, iv.size, null);
        EVP_DecryptInit_ex(ctx, null, null, key.toUByteArray().toCValues(), iv.toUByteArray().toCValues());
        EVP_CIPHER_CTX_set_padding(ctx, 1);
        
        var processing = true;
        while (processing) {
            val step = chunkProvider();
            when (step) {
                is DecryptionStep.InputChunk -> {
                    memScoped {
                        val output = allocArray<UByteVar>(step.data.size);
                        val outputLen: IntVar = alloc<IntVar>();
                        EVP_DecryptUpdate(
                            ctx,
                            output,
                            outputLen.ptr,
                            step.data.toUByteArray().toCValues(),
                            step.data.size
                        );
                        chunkConsumer(output.readBytes(outputLen.value).toUByteArray());
                    }
                }
                is DecryptionStep.EndOfInput -> {
                    step.authTag?.let {
                        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_CCM_SET_TAG, it.size, it.toCValues());
                    }

                    memScoped {
                        val output = allocArray<UByteVar>(1024);
                        val outputLen: IntVar = alloc<IntVar>();
                        val ret = EVP_DecryptFinal_ex(ctx, output, outputLen.ptr);
                    }
                    processing = false;
                }
            }
        }

        EVP_CIPHER_CTX_free(ctx);
    }

    fun decrypt(iv: ByteArray, key: ByteArray, hasAuthTag: Boolean = true, input: NSInputStream, inputLength: Int, output: NSOutputStream) {
        val len = if (hasAuthTag) { inputLength - 16 } else { inputLength }
        var consumed = 0;
        decrypt(iv, key, chunkProvider = {
            memScoped {
                val maxSize = minOf(4096, len - consumed);
                if (maxSize > 0) {
                    val buffer = allocArray<UByteVar>(maxSize);
                    val read = input.read(buffer, maxSize.toULong()).toInt();
                    if (read >= 0) {
                        consumed += read;
                        return@memScoped DecryptionStep.InputChunk(data = buffer.readBytes(read));
                    } else {
                        return@memScoped DecryptionStep.EndOfInput(authTag = null)
                    }
                }

                if (hasAuthTag) {
                    val buffer = allocArray<UByteVar>(16);
                    val read = input.read(buffer, 16u).toInt();
                    return@memScoped DecryptionStep.EndOfInput(authTag = buffer.readBytes(read));
                } else {
                    TODO("SHOULD NOT HAPPEN!");
                }
            }
        }, chunkConsumer = {
            memScoped {
                output.write(it.toCValues().ptr, it.size.toULong());
            }
        })
    }

    fun decrypt(iv: ByteArray, key: ByteArray, hasAuthTag: Boolean = true, input: NSInputStream, inputLength: Int): ByteArray {
        var result = ByteArray(0);
        val len = if (hasAuthTag) { inputLength - 16 } else { inputLength }
        var consumed = 0;
        decrypt(iv, key, chunkProvider = {
            memScoped {
                val maxSize = minOf(4096, len - consumed);
                if (maxSize > 0) {
                    val buffer = allocArray<UByteVar>(maxSize);
                    val read = input.read(buffer, maxSize.toULong()).toInt();
                    if (read >= 0) {
                        consumed += read;
                        return@memScoped DecryptionStep.InputChunk(data = buffer.readBytes(read));
                    } else {
                        return@memScoped DecryptionStep.EndOfInput(authTag = null)
                    }
                }

                if (hasAuthTag) {
                    val buffer = allocArray<UByteVar>(16);
                    val read = input.read(buffer, 16u).toInt();
                    return@memScoped DecryptionStep.EndOfInput(authTag = buffer.readBytes(read));
                } else {
                    TODO("SHOULD NOT HAPPEN!");
                }
            }
        }, chunkConsumer = {
            result = result.plus(it.toByteArray());
        })
        return result;
    }

    class Encrypted(val data: ByteArray, val tag: ByteArray) {}

    fun encrypt(iv: ByteArray, key: ByteArray, payload: ByteArray): Encrypted {
        val ctx = EVP_CIPHER_CTX_new();
        val cipher = if (key.size == 32) { EVP_aes_256_gcm() } else { EVP_aes_128_gcm() };
        EVP_EncryptInit_ex(ctx, cipher, null, null, null);
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, iv.size, null);
        EVP_EncryptInit_ex(ctx, null, null, key.toUByteArray().toCValues(), iv.toUByteArray().toCValues());
        EVP_CIPHER_CTX_set_padding(ctx, 1);

        val encrypted = memScoped {
            val output = allocArray<UByteVar>(payload.size);
            val outputLen: IntVar = alloc<IntVar>();
            EVP_EncryptUpdate(ctx, output, outputLen.ptr, payload.toUByteArray().toCValues(), payload.size);

            val result = output.readBytes(outputLen.value);

            EVP_EncryptFinal_ex(ctx, output, outputLen.ptr);

            val tag = UByteArray(16);
            EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, 16, tag.toCValues());
            return@memScoped Encrypted(data = result, tag = tag.toByteArray());
        }

        EVP_CIPHER_CTX_free(ctx);
        return encrypted;
    }

    fun encrypt(iv: ByteArray, key: ByteArray, includeAuthTag: Boolean = true, chunkProvider: () -> EncryptionStep, chunkConsumer: (UByteArray) -> Unit) {
        val ctx = EVP_CIPHER_CTX_new();
        val cipher = if (key.size == 32) { EVP_aes_256_gcm() } else { EVP_aes_128_gcm() };
        EVP_EncryptInit_ex(ctx, cipher, null, null, null);
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, iv.size, null);
        EVP_EncryptInit_ex(ctx, null, null, key.toUByteArray().toCValues(), iv.toUByteArray().toCValues());
        EVP_CIPHER_CTX_set_padding(ctx, 1);
        
        var processing = true;
        while (processing) {
            val step = chunkProvider();
            when (step) {
                is EncryptionStep.InputChunk -> {
                    memScoped {
                        val output = allocArray<UByteVar>(step.data.size * 2);
                        val outputLen: IntVar = alloc<IntVar>();
                        EVP_EncryptUpdate(
                            ctx,
                            output,
                            outputLen.ptr,
                            step.data.toUByteArray().toCValues(),
                            step.data.size
                        );
                        chunkConsumer(output.readBytes(outputLen.value).toUByteArray());
                    }
                }

                is EncryptionStep.EndOfInput -> {
                    memScoped {
                        val output = allocArray<UByteVar>(1024);
                        val outputLen: IntVar = alloc<IntVar>();
                        EVP_EncryptFinal_ex(ctx, output, outputLen.ptr);
                        chunkConsumer(output.readBytes(outputLen.value).toUByteArray());
                        if (includeAuthTag) {
                            val tag = allocArray<UByteVar>(16);
                            EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, 16, tag);
                            chunkConsumer(tag.readBytes(outputLen.value).toUByteArray());
                        }
                    }
                    processing = false;
                }
            }
        }

        EVP_CIPHER_CTX_free(ctx);
    }

    sealed class EncryptionStep {
        class InputChunk(val data: ByteArray): EncryptionStep() {}
        class EndOfInput: EncryptionStep() {}
    }

    fun encrypt(iv: ByteArray, key: ByteArray, includeAuthTag: Boolean = true, input: NSInputStream, output: NSOutputStream) {
        encrypt(iv, key, includeAuthTag, chunkProvider = {
            memScoped {
                val buffer = allocArray<UByteVar>(4096);
                val read = input.read(buffer, 4096u).toInt();
                if (read > 0) {
                    return@memScoped EncryptionStep.InputChunk(buffer.readBytes(read));
                } else {
                    return@memScoped EncryptionStep.EndOfInput();
                }
            }
        }, chunkConsumer = { chunk ->
            memScoped {
                output.write(chunk.toCValues().ptr, chunk.size.toULong());
            }
        })
    }

    fun encrypt(iv: ByteArray, key: ByteArray, includeAuthTag: Boolean = true, input: NSInputStream): ByteArray {
        var result = ByteArray(0)
        encrypt(iv, key, includeAuthTag, chunkProvider = {
            memScoped {
                val buffer = allocArray<UByteVar>(4096);
                val read = input.read(buffer, 4096u).toInt();
                if (read > 0) {
                    return@memScoped EncryptionStep.InputChunk(buffer.readBytes(read));
                } else {
                    return@memScoped EncryptionStep.EndOfInput();
                }
            }
        }, chunkConsumer = { chunk ->
            result = result.plus(chunk.toByteArray());
        })
        return result;
    }

}

private fun Message.replaceBody(newBody: String) {
    this.getChildren("body").forEach { this.remove(it) }
    this.add(element("body") {
        +newBody
    })
}
