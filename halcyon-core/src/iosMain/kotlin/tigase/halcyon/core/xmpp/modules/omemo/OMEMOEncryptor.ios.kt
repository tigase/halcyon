@file:Suppress("UnusedVariable", "UNUSED_VARIABLE", "UnusedParameter", "UNUSED_PARAMETER", "unused")
@file:OptIn(ExperimentalForeignApi::class)

package tigase.halcyon.core.xmpp.modules.omemo

import OpenSSL.*
import korlibs.encoding.hex
import kotlinx.cinterop.*
import platform.Foundation.NSInputStream
import platform.Foundation.NSOutputStream
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import tigase.halcyon.core.fromBase64
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.toBase64
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.modules.mix.getMixAnnotation
import tigase.halcyon.core.xmpp.modules.uniqueId.getStanzaIDBy
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.toBareJID

actual object OMEMOEncryptor {
    
    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.omemo.OMEMOEncryptor")
    private val engine = AesGcmEngine();
    
    private fun retrieveKey(
        keyElements: List<Element>,
        senderAddr: SignalProtocolAddress,
        store: SignalProtocolStore,
        session: OMEMOSession,
        healSession: (SignalProtocolAddress) -> Unit
    ): DecryptedKey? {
        val localKeys = keyElements.filter { it.attributes["rid"]?.toInt() == session.localRegistrationId };
        val iterator = localKeys.iterator()
        val sessionCipher = SessionCipher(store, senderAddr)
        while (iterator.hasNext()) {
            val keyElement = iterator.next()
            val preKey = keyElement.attributes["prekey"] in listOf("1", "true")
            val encryptedKey = keyElement.value?.fromBase64() ?: continue

            
            try {
                return DecryptedKey(sessionCipher.decrypt(data = encryptedKey, isPreKey = preKey), preKey);
            } catch (e: Exception) {
                // should we try to heal sessions?
                if (e is SignalException) {
                    when (e.error) {
                        // we need to try to recover
                        SignalError.invalidMessage, SignalError.noSession-> healSession(senderAddr)
                        else -> {}
                    }
                }
                if (log.isLoggable(Level.FINEST)) {
                    log.finest(e, { "failed to decrypt key for " + sessionCipher.address + ", store = " + sessionCipher.store + ", error = " + e.message })
                }
                if (iterator.hasNext()) {
                    continue
                }
                throw e
            }
        }
        return null
    }

    private fun findKeyElements(encElement: Element): List<Element> =
        encElement.getFirstChild("header")?.getChildren("key") ?: emptyList()

    data class DecryptedKey(val key: ByteArray, val isPreKey: Boolean) {}
    
    actual fun decrypt(
        store: SignalProtocolStore,
        session: OMEMOSession,
        stanza: Message,
        healSession: (SignalProtocolAddress)->Unit
    ): OMEMOMessage {
        var hasCipherText = false
        try {
            val myAddr = SignalProtocolAddress(session.localJid.toString(), session.localRegistrationId)
            val encElement =
                stanza.getChildrenNS("encrypted", OMEMOModule.XMLNS) ?: throw OMEMOException.NoEncryptedElement()
            val ciphertext = encElement.getFirstChild("payload")?.value?.fromBase64()?.also {
                hasCipherText = true
            }
            val senderId = encElement.getFirstChild("header")?.attributes?.get("sid")?.toInt()
                ?: throw OMEMOException.NoSidAttribute()
            val senderAddr = SignalProtocolAddress((stanza.getMixAnnotation()?.jid ?: stanza.attributes["from"]!!.toBareJID()).toString(), senderId)
            val iv = encElement.getFirstChild("header")?.getFirstChild("iv")?.value?.fromBase64()
                ?: throw OMEMOException.NoIV()

            // extracting inner key
            var decryptedKey =
                retrieveKey(findKeyElements(encElement), senderAddr, store, session, {
                    if (hasCipherText) {
                        healSession(it)
                    }
                })
                    ?: throw OMEMOException.DeviceKeyNotFoundException();

            ciphertext?.let {
                val key = decryptedKey.key;
                if (key.size < 32) {
                    throw OMEMOException.InvalidKeyLengthException();
                }

                val authtaglength = key.size - 16
                //val newCipherText = ciphertext.copyOf().plus(key.copyOfRange(16, 16 + authtaglength));
                val tag = key.copyOfRange(16, key.size)
                val newKey = key.copyOfRange(0, 16);

                val result = engine.decrypt(iv, newKey, it, tag);

                val decryptedBody =(result?.decodeToString()) ?: "Cannot decrypt message.";
                stanza.replaceBody(decryptedBody)
            }

            log.fine { "Decrypted message with id ${stanza.getStanzaIDBy(session.localJid)}" }
            return OMEMOMessage.Decrypted(stanza, senderAddr, store.getIdentity(senderAddr)!!.publicKey.serialize().hex, decryptedKey.isPreKey);
        } catch (e: Exception) {
            val condition = when (e) {
                is OMEMOException -> {
                    log.warning { "Cannot decrypt message due to condition ${e.condition}: ${stanza.getAsString()}" }
                    e.condition
                }
                is SignalException -> {
                    log.warning { "Cannot decrypt message due to error ${e.error}: ${stanza.getStanzaIDBy(session.localJid)}: ${e.message} : ${stanza.getAsString()}" }
                    when (e.error) {
                        SignalError.duplicateMessage -> OMEMOErrorCondition.DuplicateMessage
                        else -> OMEMOErrorCondition.CannotDecrypt
                    }
                }
                else -> {
                    log.warning(e) { "Cannot decrypt message due to exception ${e.message} : ${stanza.getAsString()}" }
                    OMEMOErrorCondition.CannotDecrypt
                }
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
    
    fun generateIV(): ByteArray {
        memScoped {
            val data = allocArray<UByteVar>(12);
            SecRandomCopyBytes(kSecRandomDefault, 12.toULong(), data)
            return data.readBytes(12);
        }
    }
    
    fun generateKey(keySize: Int = 128): ByteArray {
        val keySizeInBytes = keySize / 8;
        memScoped {
            val data = allocArray<UByteVar>(keySizeInBytes)
            SecRandomCopyBytes(kSecRandomDefault, keySizeInBytes.toULong(), data);
            return data.readBytes(keySizeInBytes);
        }
    }

    actual fun encrypt(
        session: OMEMOSession,
        plaintext: String?
    ): Element {
        log.finest("encrypting message started...");
        log.finest("generating IV...")
        val iv = generateIV()
        log.finest("generating key...")
        val keyData = generateKey()

        log.finest("encrypting with AES...")
        val encrypted = plaintext?.let { engine.encrypt(iv, keyData, it.encodeToByteArray()) }?.also {
            log.finest("encrypted with AES and got " + it.data.size + " bytes")
        }

        val authtagPlusInnerKey = encrypted?.let { keyData.plus(it.tag) } ?: keyData

        return element("encrypted") {
            xmlns = OMEMOModule.XMLNS

            "header" {
                attributes["sid"] = "${session.localRegistrationId}"
                "iv" {
                    +iv.toBase64()
                }
                session.ciphers.map { (addr, sessionCipher) ->
                    try {
                        Pair(addr, sessionCipher.encrypt(authtagPlusInnerKey))
                    } catch (e: Throwable) {
                        log.warning { "failed to encrypt message for $addr, ${e.message}" }
                        null;
                    }
                }.filterNotNull().forEach { (addr, m) ->
                    log.finest("adding encryption key for " + addr.deviceId)
                    "key" {
                        attributes["rid"] = addr.deviceId.toString()
                        
                        if (m.isPreKey) {
                            attributes["prekey"] = "true"
                        }
                        
                        +m.data.toBase64()
                    }
                }
            }

            encrypted?.let { encrypted ->
                "payload" {
                    +encrypted.data.toBase64()
                }
            }
        }
    }

}

class AesGcmEngine {

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.omemo.OMEMOEncryptor");

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
                if (it.size > 0) {
                    output.write(it.toCValues().ptr, it.size.toULong());
                }
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

            val tag = allocArray<UByteVar>(16);
            EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, 16, tag);
            return@memScoped Encrypted(data = result, tag = tag.readBytes(16));
        }

        EVP_CIPHER_CTX_free(ctx);
        return encrypted;
    }

    @OptIn(ExperimentalStdlibApi::class)
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
                        val output = allocArray<UByteVar>(8*1024);
                        val outputLen: IntVar = alloc<IntVar>();
                        EVP_EncryptFinal_ex(ctx, output, outputLen.ptr);
                        chunkConsumer(output.readBytes(outputLen.value).toUByteArray());
                        if (includeAuthTag) {
                            val tag = allocArray<UByteVar>(16);
                            EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, 16, tag);
                            val tagData = tag.readBytes(16).toUByteArray();
                            log.finest { "prepared tag data of ${tagData.size}: ${tagData.toHexString()}" }
                            chunkConsumer(tagData);
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
        var encrypted = 0;
        var wrote = 0;
        encrypt(iv, key, includeAuthTag, chunkProvider = {
            memScoped {
                val buffer = allocArray<UByteVar>(4096);
                val read = input.read(buffer, 4096u).toInt();
                if (read > 0) {
                    encrypted += read;
                    return@memScoped EncryptionStep.InputChunk(buffer.readBytes(read));
                } else {
                    return@memScoped EncryptionStep.EndOfInput();
                }
            }
        }, chunkConsumer = { chunk ->
            memScoped {
                if (chunk.size > 0) {
                    wrote += chunk.size;
                    output.write(chunk.toCValues().ptr, chunk.size.toULong());
                }
            }
        })
        log.finest("encrypted file of $encrypted bytes resulting in $wrote")
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
