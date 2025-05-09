package tigase.halcyon.core.xmpp.modules.omemo

import cnames.structs.signal_buffer
import kotlinx.cinterop.*
import libsignal.*
import platform.CoreCrypto.*
import platform.posix.size_t
import platform.posix.uint8_tVar

@OptIn(ExperimentalForeignApi::class)
val signalCryptoProvider: Lazy<signal_crypto_provider> = lazy {
    val provider = kotlinx.cinterop.nativeHeap.alloc<signal_crypto_provider>()
    provider.random_func = staticCFunction { data, len, ctx ->
        random_func(data, len, ctx)
    }
    provider.hmac_sha256_init_func = staticCFunction { hmacCtx, key, len, ctx ->
        hmac_sha256_init_func(hmacCtx, key, len, ctx)
    }
    provider.hmac_sha256_update_func = staticCFunction { hmacCtx, key, len, ctx ->
        hmac_sha256_update_func(hmacCtx, key, len, ctx)
    }
    provider.hmac_sha256_final_func = staticCFunction { hmacCtx, output, ctx ->
        hmac_sha256_final_func(hmacCtx, output, ctx)
    }
    provider.hmac_sha256_cleanup_func = staticCFunction { hmacCtx, ctx ->
        hmac_sha256_cleanup_func(hmacCtx, ctx)
    }
    provider.sha512_digest_init_func = staticCFunction { digestCtx, ctx ->
        sha512_digest_init_func(digestCtx, ctx)
    }
    provider.sha512_digest_update_func = staticCFunction { digestCtx, data, len, ctx ->
        sha512_digest_update_func(digestCtx, data, len, ctx)
    }
    provider.sha512_digest_final_func = staticCFunction { digestCtx, output, ctx ->
        sha512_digest_final_func(digestCtx, output, ctx)
    }
    provider.sha512_digest_cleanup_func = staticCFunction { digestCtx, ctx ->
        sha512_digest_cleanup_func(digestCtx, ctx)
    }
    provider.encrypt_func =
        staticCFunction { output, cipher, key, keyLen, iv, ivLen, plaintext, plaintextLen, ctx ->
            encrypt_func(output, cipher, key, keyLen, iv, ivLen, plaintext, plaintextLen, ctx)
        }
    provider.decrypt_func =
        staticCFunction { output, cipher, key, keyLen, iv, ivLen, ciphertext, ciphertextLen, ctx ->
            decrypt_func(output, cipher, key, keyLen, iv, ivLen, ciphertext, ciphertextLen, ctx)
        }
    return@lazy provider
}

@OptIn(ExperimentalForeignApi::class)
fun random_func(data: CPointer<UByteVarOf<UByte>>?, len: ULong, ctx: COpaquePointer?): Int {
    if (CCRandomGenerateBytes(data, len) != kCCSuccess) {
        return SG_ERR_INVAL
    }
    return SG_SUCCESS
}

@OptIn(ExperimentalForeignApi::class)
fun hmac_sha256_init_func(
    hmacCtx: CPointer<COpaquePointerVar>?,
    key: CPointer<uint8_tVar>?,
    keyLen: size_t,
    ctx: COpaquePointer?
): Int {
    if (!(hmacCtx != null && key != null)) {
        return SG_ERR_INVAL
    }

    val newHmacCtx = nativeHeap.alloc<CCHmacContext>()
    CCHmacInit(newHmacCtx.ptr, kCCHmacAlgSHA256, key, keyLen)
    hmacCtx.pointed.value = newHmacCtx.ptr
    return SG_SUCCESS
}

@OptIn(ExperimentalForeignApi::class)
fun hmac_sha256_update_func(
    hmacCtx: COpaquePointer?,
    key: CPointer<uint8_tVar>?,
    len: size_t,
    ctx: COpaquePointer?
): Int {
    if (!(hmacCtx != null && key != null)) {
        return SG_ERR_INVAL
    }

    CCHmacUpdate(hmacCtx.reinterpret(), key, len)
    return SG_SUCCESS
}

@OptIn(ExperimentalForeignApi::class)
fun hmac_sha256_final_func(
    hmacCtx: COpaquePointer?,
    output: CPointer<CPointerVar<signal_buffer>>?,
    ctx: COpaquePointer?
): Int {
    if (!(hmacCtx != null && output != null)) {
        return SG_ERR_INVAL
    }

    memScoped {
        val data = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
        CCHmacFinal(hmacCtx.reinterpret(), data)
        output.pointed.value = signal_buffer_create(data, CC_SHA256_DIGEST_LENGTH.toULong())
    }

    return SG_SUCCESS
}

@OptIn(ExperimentalForeignApi::class)
fun hmac_sha256_cleanup_func(hmacCtx: COpaquePointer?, ctx: COpaquePointer?): Int {
    if (hmacCtx == null) {
        return SG_ERR_INVAL
    }

    nativeHeap.free(hmacCtx)

    return SG_SUCCESS
}

@OptIn(ExperimentalForeignApi::class)
fun sha512_digest_init_func(digestCtx: CPointer<COpaquePointerVar>?, ctx: COpaquePointer?): Int {
    if (!(digestCtx != null)) {
        return SG_ERR_INVAL
    }

    val newCtx = nativeHeap.alloc<CC_SHA512_CTX>()
    CC_SHA512_Init(newCtx.ptr)
    digestCtx.pointed.value = newCtx.ptr
    return SG_SUCCESS
}

@OptIn(ExperimentalForeignApi::class)
fun sha512_digest_update_func(
    digestCtx: COpaquePointer?,
    data: CPointer<uint8_tVar>?,
    len: size_t,
    ctx: COpaquePointer?
): Int {
    if (!(digestCtx != null && data != null)) {
        return SG_ERR_INVAL
    }

    CC_SHA512_Update(digestCtx.reinterpret(), data, len.toUInt())
    return SG_SUCCESS
}

@OptIn(ExperimentalForeignApi::class)
fun sha512_digest_final_func(
    digestCtx: COpaquePointer?,
    output: CPointer<CPointerVar<signal_buffer>>?,
    ctx: COpaquePointer?
): Int {
    if (!(digestCtx != null && output != null)) {
        return SG_ERR_INVAL
    }

//    val data = UByteArray(CC_SHA512_DIGEST_LENGTH);
//    CC_SHA512_Final(data.toCValues(), digestCtx.reinterpret());

    memScoped {
        val data = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
        CC_SHA512_Final(data, digestCtx.reinterpret())
        output.pointed.value = signal_buffer_create(data, CC_SHA512_DIGEST_LENGTH.toULong())
    }
    return SG_SUCCESS
}

@OptIn(ExperimentalForeignApi::class)
fun sha512_digest_cleanup_func(digestCtx: COpaquePointer?, ctx: COpaquePointer?): Int {
    if (digestCtx == null) {
        return SG_ERR_INVAL
    }

    nativeHeap.free(digestCtx)

    return SG_SUCCESS
}

@OptIn(ExperimentalForeignApi::class)
fun encrypt_func(
    output: CPointer<CPointerVar<signal_buffer>>?,
    cipher: Int,
    key: CPointer<uint8_tVar>?,
    keyLen: size_t,
    iv: CPointer<uint8_tVar>?,
    ivLen: size_t,
    plaintext: CPointer<uint8_tVar>?,
    plaintextLen: size_t,
    ctx: COpaquePointer?
): Int {
    if (cipher != SG_CIPHER_AES_CBC_PKCS5) {
        return SG_ERR_INVAL
    }
    memScoped {
        val outputLen = alloc<ULongVar>()
//        val data = UByteArray((kCCBlockSizeAES128 + plaintextLen).toInt());
//        val result = CCCrypt(kCCEncrypt, kCCAlgorithmAES, kCCOptionPKCS7Padding, key, keyLen, iv, plaintext, plaintextLen, data.toCValues(), data.size.toULong(), outputLen.ptr);
//        if (result != kCCSuccess) {
//            return SG_ERR_UNKNOWN;
//        }
//
//        output!!.pointed.value = signal_buffer_create(data.toCValues(), outputLen.value);

        val dataLen = (kCCBlockSizeAES128 + plaintextLen)
        val data = allocArray<UByteVar>(dataLen.toInt())
        val result =
            CCCrypt(kCCEncrypt, kCCAlgorithmAES, kCCOptionPKCS7Padding, key, keyLen, iv, plaintext, plaintextLen, data, dataLen, outputLen.ptr)
        if (result != kCCSuccess) {
            return SG_ERR_UNKNOWN
        }

        output!!.pointed.value = signal_buffer_create(data, outputLen.value)

        return SG_SUCCESS
    }
}

@OptIn(ExperimentalForeignApi::class)
fun decrypt_func(
    output: CPointer<CPointerVar<signal_buffer>>?,
    cipher: Int,
    key: CPointer<uint8_tVar>?,
    keyLen: size_t,
    iv: CPointer<uint8_tVar>?,
    ivLen: size_t,
    ciphertext: CPointer<uint8_tVar>?,
    ciphertextLen: size_t,
    ctx: COpaquePointer?
): Int {
    if (cipher != SG_CIPHER_AES_CBC_PKCS5) {
        return SG_ERR_INVAL
    }
    memScoped {
        val outputLen = alloc<ULongVar>()
//        val data = UByteArray((kCCBlockSizeAES128 + ciphertextLen).toInt());
//        val result = CCCrypt(kCCDecrypt, kCCAlgorithmAES, kCCOptionPKCS7Padding, key, keyLen, iv, ciphertext, ciphertextLen, data.toCValues(), data.size.toULong(), outputLen.ptr);
//        if (result != kCCSuccess) {
//            return SG_ERR_UNKNOWN;
//        }
//
//        output!!.pointed.value = signal_buffer_create(data.toCValues(), outputLen.value);

        val dataLen = kCCBlockSizeAES128 + ciphertextLen
        val data = allocArray<UByteVar>(dataLen.toInt())
        val result =
            CCCrypt(kCCDecrypt, kCCAlgorithmAES, kCCOptionPKCS7Padding, key, keyLen, iv, ciphertext, ciphertextLen, data, dataLen, outputLen.ptr)
        if (result != kCCSuccess) {
            return SG_ERR_UNKNOWN
        }

        output!!.pointed.value = signal_buffer_create(data, outputLen.value)

        return SG_SUCCESS
    }
}
