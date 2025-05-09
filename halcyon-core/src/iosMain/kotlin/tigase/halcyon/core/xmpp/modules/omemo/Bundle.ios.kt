@file:OptIn(
    ExperimentalForeignApi::class,
    ExperimentalForeignApi::class,
    ExperimentalForeignApi::class,
    ExperimentalForeignApi::class
)

package tigase.halcyon.core.xmpp.modules.omemo

import cnames.structs.ec_public_key
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import libsignal.CIPHERTEXT_PREKEY_TYPE
import libsignal.ciphertext_message_get_serialized
import libsignal.ciphertext_message_get_type
import libsignal.curve_decode_point
import libsignal.ec_key_pair_get_private
import libsignal.ec_key_pair_get_public
import libsignal.ec_private_key_serialize
import libsignal.ec_public_key_serialize
import libsignal.pre_key_signal_message_deserialize
import libsignal.ratchet_identity_key_pair_deserialize
import libsignal.ratchet_identity_key_pair_get_private
import libsignal.ratchet_identity_key_pair_get_public
import libsignal.session_builder_create
import libsignal.session_builder_process_pre_key_bundle
import libsignal.session_cipher_create
import libsignal.session_cipher_decrypt_pre_key_signal_message
import libsignal.session_cipher_decrypt_signal_message
import libsignal.session_cipher_encrypt
import libsignal.session_cipher_free
import libsignal.session_pre_key_bundle_create
import libsignal.session_pre_key_deserialize
import libsignal.session_pre_key_get_id
import libsignal.session_pre_key_get_key_pair
import libsignal.session_signed_pre_key_deserialize
import libsignal.session_signed_pre_key_get_id
import libsignal.session_signed_pre_key_get_key_pair
import libsignal.session_signed_pre_key_get_signature
import libsignal.session_signed_pre_key_get_signature_len
import libsignal.signal_buffer_free
import libsignal.signal_message_deserialize
import libsignal.signal_protocol_address
import libsignal.signal_type_unref
import platform.Foundation.NSInputStream
import platform.Foundation.NSOutputStream
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun Bundle.getRandomPreKeyBundle(): PreKeyBundle {
    val preKey = this.preKeys.random()
    return PreKeyBundle(
        this.deviceId,
        this.deviceId,
        preKey,
        this
    )
}

actual interface CiphertextMessage

@OptIn(ExperimentalForeignApi::class)
actual class IdentityKey actual constructor(val publicKey: ECPublicKey) {

    actual constructor(byteArray: ByteArray, offset: Int) : this(
        ECPublicKeyImpl(publicKey = byteArray)
    ) {
    }

    @OptIn(ExperimentalStdlibApi::class)
    actual fun getFingerprint(): String = getPublicKey().serialize().toHexString()

    actual fun getPublicKey(): ECPublicKey = publicKey

    actual fun serialize(): ByteArray = publicKey.serialize()

    fun pointer(fn: (CPointer<ec_public_key>) -> Unit) {
        publicKey.pointer(fn)
    }
}

class ECPrivateKey(val privateKey: ByteArray) {

    companion object {
        @OptIn(ExperimentalForeignApi::class)
        fun fromPointer(pointer: CPointer<cnames.structs.ec_private_key>): ECPrivateKey {
            return memScoped {
                val privateKeyBuf = allocPointerTo<cnames.structs.signal_buffer>()
                ec_private_key_serialize(privateKeyBuf.ptr, pointer)
                val result = ECPrivateKey(privateKeyBuf.value!!.toByteArray())
                signal_buffer_free(privateKeyBuf.value)
                return result
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class IdentityKeyPair actual constructor(val data: ByteArray) {

    private class Content(val publicKey: IdentityKey, val privateKey: ECPrivateKey)

    private val content: Lazy<Content> = lazy {
        pointerKeyPair { keyPair ->
            memScoped {
                val publicKeyPtr = ratchet_identity_key_pair_get_public(keyPair)
                val privateKeyPtr = ratchet_identity_key_pair_get_private(keyPair)
                val identityKey = IdentityKey(ECPublicKey.fromPointer(publicKeyPtr!!))
                val privateKey = ECPrivateKey.fromPointer(privateKeyPtr!!)
                return@memScoped Content(identityKey, privateKey)
            }
        }
    }

    actual fun getPublicKey(): IdentityKey = content.value.publicKey

    fun getPrivateKey(): ECPrivateKey = content.value.privateKey

    actual fun serialize(): ByteArray = data

    fun <T> pointerKeyPair(fn: (CPointer<cnames.structs.ratchet_identity_key_pair>) -> T): T {
        return memScoped {
            val pointer = allocPointerTo<cnames.structs.ratchet_identity_key_pair>()
            if (ratchet_identity_key_pair_deserialize(
                    key_pair = pointer.ptr,
                    data.toUByteArray().toCValues(),
                    data.size.toULong(),
                    null
                ) <
                0
            ) {
                TODO("Not yet implemented")
            }
            val result = fn(pointer.value!!)
            pointer.value?.let {
                signal_type_unref(it.reinterpret())
            }
            return@memScoped result
        }
    }
}

actual class SignedPreKeyRecord actual constructor(val data: ByteArray) {

    private data class Metadata(val id: Int, val keyPair: ECKeyPair, val signature: ByteArray)

    private val metadata: Lazy<Metadata> = lazy {
        return@lazy pointer { record ->
            memScoped {
                val id = session_signed_pre_key_get_id(record)
                val signature = session_signed_pre_key_get_signature(record)!!.readBytes(
                    session_signed_pre_key_get_signature_len(record).toInt()
                )
                val ecKeyPairPtr = session_signed_pre_key_get_key_pair(record)
                val keyPair = ECKeyPair.fromPointer(ecKeyPairPtr)
                return@pointer Metadata(id = id.toInt(), keyPair = keyPair, signature = signature)
            }
        }
    }

    actual fun getId(): Int = metadata.value.id

    actual fun getKeyPair(): ECKeyPair = metadata.value.keyPair

    actual fun getSignature(): ByteArray = metadata.value.signature

    actual fun serialize(): ByteArray = data

    @OptIn(ExperimentalForeignApi::class)
    fun <T> pointer(fn: (CPointer<cnames.structs.session_signed_pre_key>) -> T): T {
        return memScoped {
            val pointer = allocPointerTo<cnames.structs.session_signed_pre_key>()
            if (session_signed_pre_key_deserialize(
                    pointer.ptr,
                    data.toUByteArray().toCValues(),
                    data.size.toULong(),
                    null
                ) <
                0
            ) {
                TODO("Not yet implemented")
            }
            val result = fn(pointer.value!!)
            signal_type_unref(pointer.value!!.reinterpret())
            return result
        }
    }
}

actual class ECKeyPair(val privateKey: ByteArray, val publicKey: ByteArray) {

    @OptIn(ExperimentalForeignApi::class)
    companion object {
        fun fromPointer(keyPairPointer: CPointer<cnames.structs.ec_key_pair>?): ECKeyPair {
            return memScoped {
                val publicKeyBuf = allocPointerTo<cnames.structs.signal_buffer>()
                val privateKeyBuf = allocPointerTo<cnames.structs.signal_buffer>()
                var result =
                    ec_public_key_serialize(
                        publicKeyBuf.ptr,
                        ec_key_pair_get_public(keyPairPointer)
                    )
                if (result < 0) {
                    TODO("Not yet implemented")
                }
                result =
                    ec_private_key_serialize(
                        privateKeyBuf.ptr,
                        ec_key_pair_get_private(keyPairPointer)
                    )
                if (result < 0) {
                    TODO("Not yet implemented")
                }

                val keyPair = ECKeyPair(
                    privateKey = privateKeyBuf.value!!.toByteArray(),
                    publicKey = publicKeyBuf.value!!.toByteArray()
                )
                signal_buffer_free(privateKeyBuf.value)
                signal_buffer_free(publicKeyBuf.value)
                return@memScoped keyPair
            }
        }
    }

    actual fun getPublicKey(): ECPublicKey = ECPublicKeyImpl(publicKey)
}

actual class PreKeyRecord actual constructor(val data: ByteArray) {

    private data class Metadata(val id: Int, val keyPair: ECKeyPair)

    private val metadata: Lazy<Metadata> = lazy {
        return@lazy memScoped {
            val pointer = allocPointerTo<cnames.structs.session_pre_key>()
            val result =
                session_pre_key_deserialize(
                    pointer.ptr,
                    data.toUByteArray().toCValues(),
                    data.size.toULong(),
                    null
                )
            if (result < 0) {
                TODO("Not yet implemented")
            }
            val id = session_pre_key_get_id(pointer.value)
            val keyPair = ECKeyPair.fromPointer(session_pre_key_get_key_pair(pointer.value))
            signal_type_unref(pointer.value!!.reinterpret())
            return@memScoped Metadata(id = id.toInt(), keyPair = keyPair)
        }
    }

    actual fun getId(): Int = metadata.value.id

    actual fun getKeyPair(): ECKeyPair = metadata.value.keyPair

    actual fun serialize(): ByteArray = data
}

actual interface ECPublicKey {

    companion object {
        @OptIn(ExperimentalForeignApi::class)
        fun fromPointer(pointer: CPointer<cnames.structs.ec_public_key>): ECPublicKey {
            return memScoped {
                val publicKeyBuf = allocPointerTo<cnames.structs.signal_buffer>()
                if (ec_public_key_serialize(publicKeyBuf.ptr, pointer) < 0) {
                    TODO("Not yet implemented")
                }
                val result = ECPublicKeyImpl(publicKeyBuf.value!!.toByteArray())
                signal_buffer_free(publicKeyBuf.value)
                return@memScoped result
            }
        }
    }

    actual fun serialize(): ByteArray

    fun pointer(fn: (CPointer<ec_public_key>) -> Unit)
}

@OptIn(ExperimentalForeignApi::class)
class ECPublicKeyImpl(val publicKey: ByteArray) : ECPublicKey {

    override fun serialize(): ByteArray = publicKey

    override fun pointer(fn: (CPointer<ec_public_key>) -> Unit) {
        memScoped {
            val pointer = allocPointerTo<ec_public_key>()
            val error = SignalError.from(
                curve_decode_point(
                    public_key = pointer.ptr,
                    publicKey.toUByteArray().toCValues(),
                    publicKey.size.toULong(),
                    null
                )
            )
            error?.let {
                LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.omemo.ECPublicKeyImpl")
                    .warning("failed to decode curve point: ${error.name}")
                throw NullPointerException("failed to decode curve point: ${error.name}")
            }
            fn(pointer.value!!)
            signal_type_unref(pointer.value!!.reinterpret())
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class PreKeyBundle(
    val registrationId: Int,
    val deviceId: Int,
    val preKey: PreKey,
    val bundle: Bundle
) {

    fun pointer(fn: (CPointer<cnames.structs.session_pre_key_bundle>) -> Unit) {
        ECPublicKeyImpl(publicKey = preKey.preKeyPublic).pointer { preKeyPublic ->
            ECPublicKeyImpl(publicKey = bundle.signedPreKeyPublic).pointer { signedPreKeyPublic ->
                ECPublicKeyImpl(publicKey = bundle.identityKey).pointer { identityKey ->
                    memScoped {
                        var pointer = allocPointerTo<cnames.structs.session_pre_key_bundle>()
                        if (session_pre_key_bundle_create(
                                pointer.ptr,
                                0u, // registrationId.toUInt(),
                                device_id = deviceId,
                                pre_key_id = preKey.preKeyId.toUInt(),
                                pre_key_public = preKeyPublic,
                                signed_pre_key_id = bundle.signedPreKeyId.toUInt(),
                                signed_pre_key_public = signedPreKeyPublic,
                                signed_pre_key_signature_data = bundle.signedPreKeySignature.toUByteArray().toCValues(),
                                signed_pre_key_signature_len = bundle.signedPreKeySignature.size.toULong(),
                                identity_key = identityKey
                            ) >= 0
                        ) {
                            pointer.value?.let {
                                fn(it)
                                signal_type_unref(it.reinterpret())
                            }
                        }
                    }
                }
            }
        }
    }

    override fun toString(): String =
        "PreKeyBundle(registrationId=$registrationId, deviceId=$deviceId, preKey=$preKey, bundle=$bundle)"
}

@OptIn(ExperimentalForeignApi::class)
actual class SessionBuilder actual constructor(
    val store: SignalProtocolStore,
    val address: SignalProtocolAddress
) {

    private val logger = LoggerFactory.logger(
        "tigase.halcyon.core.xmpp.modules.omemo.SessionBuilder"
    )

    @Throws(InvalidKeyException::class, UntrustedIdentityException::class)
    actual fun process(preKeyBundle: PreKeyBundle) {
        memScoped {
            var pointer = allocPointerTo<cnames.structs.session_builder>()
            address.pointer { addressPtr ->
                logger.finest {
                    "calling session_builder_create(), " + store.signalStoreContext +
                        ", " +
                        address +
                        ", " +
                        store.signalContext.context
                }
                val builderCreationError = SignalError.from(
                    session_builder_create(
                        pointer.ptr,
                        store.signalStoreContext!!,
                        addressPtr,
                        store.signalContext.context
                    )
                )
                builderCreationError?.let {
                    logger.warning("failed to create session builder for $address")
                } ?: preKeyBundle.pointer { bundle ->
                    logger.finest {
                        "calling session_builder_process_pre_key_bundle(), " +
                            preKeyBundle
                    }
                    // processPreKeyBundle(pointer.value!!, bundle, store, address);
                    val error = SignalError.from(
                        session_builder_process_pre_key_bundle(pointer.value, bundle)
                    )
                    error?.let {
                        logger.warning(
                            "session creation failed for $address with error: " + it.name
                        )
                    }
                        ?: logger.info("session created successfully for $address")
                }
            }
        }
    }
}

actual class InvalidKeyException : Exception()

actual class UntrustedIdentityException : Exception()

enum class SignalError(val errorCode: Int) {
    notEncrypted(-100000),
    noDestination(-100001),

    noMemory(-12),
    invalidArgument(-22),
    unknown(-1000),
    duplicateMessage(-1001),
    invalidKey(-1002),
    invalidKeyId(-1003),
    invalidMac(-1004),
    invalidMessage(-1005),
    invalidVersion(-1006),
    legacyMessage(-1007),
    noSession(-1008),
    staleKeyExchange(-1009),
    untrustedIdenity(-1010),
    signatureVerificationFailed(-1011),
    invalidProtoBuf(-1100),
    fpInvalidVersion(-1200),
    fpIdentityMismatch(-1201);

    companion object {
        fun from(errorCode: Int): SignalError? {
            if (errorCode < 0) {
                return SignalError.entries.find { it.errorCode == errorCode }
            }
            return null
        }
    }
}

class SignalException(var error: SignalError, message: String?) : Exception(message) {

    constructor(error: SignalError) : this(error, "Got error: ${error.name}") {
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class SessionCipher actual constructor(
    val store: SignalProtocolStore,
    val address: SignalProtocolAddress
) {

    @Throws(SignalException::class)
    fun decrypt(data: ByteArray, isPreKey: Boolean): ByteArray {
        return pointer { cipher ->
            if (isPreKey) {
                return@pointer memScoped {
                    val preKeySignalMessagePointer =
                        allocPointerTo<cnames.structs.pre_key_signal_message>()
                    var error = SignalError.from(
                        pre_key_signal_message_deserialize(
                            preKeySignalMessagePointer.ptr,
                            data.toUByteArray().toCValues(),
                            data.size.toULong(),
                            store.signalContext.context
                        )
                    )
                    error?.let {
                        throw SignalException(
                            it,
                            "Failed to deserialize prekey signal message with error ${it.name}"
                        )
                    }
                    val output = allocPointerTo<cnames.structs.signal_buffer>()
                    preKeySignalMessagePointer.value?.let {
                        error =
                            SignalError.from(
                                session_cipher_decrypt_pre_key_signal_message(
                                    cipher,
                                    it,
                                    null,
                                    output.ptr
                                )
                            )
                        signal_type_unref(it.reinterpret())
                    }
                    error?.let {
                        throw SignalException(
                            it,
                            "Failed to decrypt prekey signal message with error ${it.name}"
                        )
                    }
                    val result = output.value!!.toByteArray()
                    signal_buffer_free(output.value)
                    return@memScoped result
                }
                // pre_key_
            } else {
                return@pointer memScoped {
                    val signalMessagePointer = allocPointerTo<cnames.structs.signal_message>()
                    var error = SignalError.from(
                        signal_message_deserialize(
                            signalMessagePointer.ptr,
                            data.toUByteArray().toCValues(),
                            data.size.toULong(),
                            store.signalContext.context
                        )
                    )
                    error?.let {
                        throw SignalException(
                            it,
                            "Failed to deserialize signal message with error ${it.name}"
                        )
                    }
                    val output = allocPointerTo<cnames.structs.signal_buffer>()
                    signalMessagePointer.value?.let {
                        error =
                            SignalError.from(
                                session_cipher_decrypt_signal_message(cipher, it, null, output.ptr)
                            )
                        signal_type_unref(it.reinterpret())
                    }
                    error?.let {
                        throw SignalException(
                            it,
                            "Failed to decrypt signal message with error ${it.name}"
                        )
                    }

                    val result = output.value!!.toByteArray()
                    signal_buffer_free(output.value)
                    return@memScoped result
                }
            }
        }
    }

    class EncryptedData(val data: ByteArray, val isPreKey: Boolean)

    private val logger = LoggerFactory.logger(
        "tigase.halcyon.core.xmpp.modules.omemo.SessionCipher"
    )

    @Throws(SignalException::class)
    fun encrypt(data: ByteArray): EncryptedData {
        return pointer { cipher ->
            return@pointer memScoped {
                val encryptedMessagePointer = allocPointerTo<cnames.structs.ciphertext_message>()
                var error = SignalError.from(
                    session_cipher_encrypt(
                        cipher,
                        data.toUByteArray().toCValues(),
                        data.size.toULong(),
                        encryptedMessagePointer.ptr
                    )
                )
                error?.let {
                    logger.finest {
                        "Failed to encrypt signal message with $address with error ${it.name}"
                    }
                    throw SignalException(
                        it,
                        "Failed to encrypt signal message with $address  with error ${it.name}"
                    )
                }
                val prekey =
                    ciphertext_message_get_type(encryptedMessagePointer.value!!) ==
                        CIPHERTEXT_PREKEY_TYPE
                val serialized = ciphertext_message_get_serialized(encryptedMessagePointer.value)!!

                val result = serialized.toByteArray()
                signal_type_unref(encryptedMessagePointer.value!!.reinterpret())
                // signal_buffer_free(serialized)
                return@memScoped EncryptedData(data = result, isPreKey = prekey)
            }
        }
    }

    private fun <T> pointer(fn: (CPointer<cnames.structs.session_cipher>) -> T): T {
        return memScoped {
            val sessionCipher = allocPointerTo<cnames.structs.session_cipher>()
            return@memScoped address.pointer { addressPtr ->
                logger.finest {
                    "calling session_cipher_create(), " + store.signalStoreContext +
                        ", " +
                        address +
                        ", " +
                        store.signalContext.context
                }
                val error = SignalError.from(
                    session_cipher_create(
                        sessionCipher.ptr,
                        store.signalStoreContext,
                        addressPtr,
                        store.signalContext.context
                    )
                )
                error?.let {
                    logger.warning { "Failed to create session cipher with error ${it.name}" }
                }
                val result = sessionCipher.value!!.let {
                    val ret = fn(it)
                    session_cipher_free(it)
                    return@let ret
                }
                return@pointer result
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class SignalProtocolAddress actual constructor(val name: String, val deviceId: Int) {

    companion object {
        fun fromPointer(pointer: CPointer<signal_protocol_address>): SignalProtocolAddress =
            SignalProtocolAddress(
                pointer.pointed.name!!.readBytes(pointer.pointed.name_len.toInt()).toKString(),
                pointer.pointed.device_id
            )
    }

    actual fun getName(): String = name
    actual fun getDeviceId(): Int = deviceId

    fun <T> pointer(fn: (CPointer<signal_protocol_address>) -> T): T {
        val nameValue = name.encodeToByteArray().toCValues()
        return memScoped {
            val pointer = alloc<signal_protocol_address>()
            pointer.device_id = deviceId
            pointer.name = nameValue.ptr
            pointer.name_len = nameValue.size.toULong()
            return@memScoped fn(pointer.ptr)
        }
    }

    override fun toString(): String = "Address[$name:$deviceId]"
}

@OptIn(ExperimentalForeignApi::class)
object OMEMOFileEncryptor {

    private val engine = AesGcmEngine()

    fun generateKeyAndIv(): ByteArray = generateIv().plus(generateKey())

    fun generateKey(): ByteArray = OMEMOEncryptor.generateKey(keySize = 256)

    fun generateIv(): ByteArray = OMEMOEncryptor.generateIV()

    internal fun getIv(data: ByteArray): ByteArray {
        if (data.size > 32) {
            return data.copyOfRange(0, data.size - 32)
        } else {
            throw HalcyonException("Key is too short.")
        }
    }

    internal fun getKey(data: ByteArray): ByteArray {
        if (data.size > 32) {
            return data.copyOfRange(data.size - 32, data.size)
        } else {
            throw HalcyonException("Key is too short.")
        }
    }

    fun encrypt(input: NSInputStream, keyAndIv: ByteArray, output: NSOutputStream) {
        val iv = getIv(keyAndIv)
        val key = getKey(keyAndIv)
        val engine = AesGcmEngine()
        engine.encrypt(iv, key, true, input, output)
    }

    fun decrypt(input: NSInputStream, inputLen: Int, keyAndIv: ByteArray, output: NSOutputStream) {
        val iv = getIv(keyAndIv)
        val key = getKey(keyAndIv)
        val engine = AesGcmEngine()
        engine.decrypt(iv, key, true, input, inputLen, output)
    }

    fun encrypt(
        input: NSInputStream,
        keyAndIv: ByteArray,
        includeAuthTag: Boolean = true
    ): ByteArray {
        val iv = getIv(keyAndIv)
        val key = getKey(keyAndIv)
        val engine = AesGcmEngine()
        return engine.encrypt(iv, key, includeAuthTag, input)
    }

    fun decrypt(input: NSInputStream, inputLen: Int, keyAndIv: ByteArray): ByteArray {
        val iv = getIv(keyAndIv)
        val key = getKey(keyAndIv)
        val engine = AesGcmEngine()
        return engine.decrypt(iv, key, true, input, inputLen)
    }
}

actual class InvalidKeyIdException actual constructor(message: String) : Exception()

actual interface PreKeyStore {
    @Throws(InvalidKeyIdException::class)
    actual fun loadPreKey(preKeyId: Int): PreKeyRecord
    actual fun storePreKey(preKeyId: Int, record: PreKeyRecord)
    actual fun containsPreKey(preKeyId: Int): Boolean
    actual fun removePreKey(preKeyId: Int)
}

actual interface SignedPreKeyStore {
    @Throws(InvalidKeyIdException::class)
    actual fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord
    actual fun loadSignedPreKeys(): List<SignedPreKeyRecord?>?
    actual fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord)

    actual fun containsSignedPreKey(signedPreKeyId: Int): Boolean
    actual fun removeSignedPreKey(signedPreKeyId: Int)
}

actual class SessionRecord actual constructor(val data: ByteArray) {
    actual fun serialize(): ByteArray = data
}

actual interface SessionStore {
    actual fun loadSession(address: SignalProtocolAddress): SessionRecord
    actual fun getSubDeviceSessions(name: String): List<Int>
    actual fun storeSession(address: SignalProtocolAddress, record: SessionRecord)

    actual fun containsSession(address: SignalProtocolAddress): Boolean
    actual fun deleteSession(address: SignalProtocolAddress)
    actual fun deleteAllSessions(name: String)
}

actual enum class IdentityKeyStoreDirection {
    SENDING,
    RECEIVING
}

actual interface IdentityKeyStore {
    actual fun getIdentityKeyPair(): IdentityKeyPair
    actual fun getLocalRegistrationId(): Int
    actual fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean

    actual fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStoreDirection
    ): Boolean

    actual fun getIdentity(address: SignalProtocolAddress): IdentityKey?
}
