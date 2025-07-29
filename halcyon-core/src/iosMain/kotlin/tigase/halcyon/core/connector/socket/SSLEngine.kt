/*
 * halcyon-core
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core.connector.socket

import OpenSSL.BIO
import OpenSSL.BIO_ctrl_pending
import OpenSSL.BIO_new
import OpenSSL.BIO_read
import OpenSSL.BIO_s_mem
import OpenSSL.BIO_write
import OpenSSL.ERR_error_string
import OpenSSL.ERR_get_error
import OpenSSL.ERR_load_CRYPTO_strings
import OpenSSL.ERR_load_ERR_strings
import OpenSSL.ERR_reason_error_string
import OpenSSL.SSL
import OpenSSL.SSL_CTRL_SET_SESS_CACHE_MODE
import OpenSSL.SSL_CTX
import OpenSSL.SSL_CTX_ctrl
import OpenSSL.SSL_CTX_new
import OpenSSL.SSL_ERROR_NONE
import OpenSSL.SSL_ERROR_WANT_READ
import OpenSSL.SSL_ERROR_WANT_WRITE
import OpenSSL.SSL_SESS_CACHE_CLIENT
import OpenSSL.SSL_SESS_CACHE_NO_INTERNAL_STORE
import OpenSSL.SSL_do_handshake
import OpenSSL.SSL_get_error
import OpenSSL.SSL_get_peer_cert_chain
import OpenSSL.SSL_load_error_strings
import OpenSSL.SSL_new
import OpenSSL.SSL_read
import OpenSSL.SSL_set_bio
import OpenSSL.SSL_set_connect_state
import OpenSSL.SSL_write
import OpenSSL.TLS_client_method
import OpenSSL.X509
import OpenSSL.i2d_X509
import OpenSSL.sk_X509_shift
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValue
import kotlinx.cinterop.getBytes
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayCreateMutable
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFArraySetValueAtIndex
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithBytes
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSLock
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecPolicyCreateBasicX509
import platform.Security.SecPolicyCreateSSL
import platform.Security.SecTrustCreateWithCertificates
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustGetTrustResult
import platform.Security.SecTrustRefVar
import platform.Security.SecTrustResultTypeVar
import platform.Security.SecTrustSetPolicies
import platform.Security.errSecSuccess
import platform.Security.kSecTrustResultProceed
import platform.Security.kSecTrustResultUnspecified
import platform.darwin.ByteVar
import tigase.halcyon.core.logger.LoggerFactory

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class SSLEngine(connector: SocketConnector, domain: String) {

    private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.SSLEngine")

    enum class HandshakeResult {
        complete,
        incomplete,
        failed;

        companion object {

            fun from(code: Int, engine: SSLEngine): HandshakeResult {
                if (code == 1) {
                    return complete
                }

                val status = SSLStatus.from(code = code, engine = engine)
                return when (status) {
                    SSLStatus.want_read, SSLStatus.want_write -> incomplete
                    else -> failed
                }
            }
        }
    }

    enum class State {
        handshaking,
        active,
        closed
    }

    enum class SSLStatus {
        ok,
        want_read,
        want_write,
        fail;

        companion object {

            private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.SSLEngine")

            fun from(code: Int, engine: SSLEngine): SSLStatus {
                if (code == 0) {
                    return ok
                }

                val status = SSL_get_error(engine.ssl, code)
                log.finest("SSLEngine got error status: " + status)
                val err = ERR_get_error()
                if (err != 0.toULong()) {
                    log.finest(
                        "error1: " + err + " - " + ERR_error_string(err, null)?.toKStringFromUtf8()
                    )
                    log.finest("error2: " + ERR_reason_error_string(err)?.toKStringFromUtf8())
                }
                return when (status) {
                    SSL_ERROR_NONE -> ok
                    SSL_ERROR_WANT_READ -> want_read
                    SSL_ERROR_WANT_WRITE -> want_write
                    else -> fail
                }
            }
        }
    }

    private val connector: SocketConnector = connector
    private val domain = domain
    private var writeBio: CPointer<BIO>?
    private var readBio: CPointer<BIO>?
    private var ctx: CPointer<SSL_CTX>?
    private var ssl: CPointer<SSL>?
    private var state: State = State.handshaking

    init {
        ctx = SSL_CTX_new(TLS_client_method())
        SSL_CTX_ctrl(
            ctx,
            SSL_CTRL_SET_SESS_CACHE_MODE,
            (SSL_SESS_CACHE_CLIENT or SSL_SESS_CACHE_NO_INTERNAL_STORE).toLong(),
            null
        )
        ssl = SSL_new(ctx)
        readBio = BIO_new(BIO_s_mem())
        writeBio = BIO_new(BIO_s_mem())
        SSL_set_bio(ssl, readBio, writeBio)
        SSL_set_connect_state(ssl)
        SSL_load_error_strings()
        ERR_load_CRYPTO_strings()
        ERR_load_ERR_strings()
    }

    fun decrypt(data: ByteArray) {
// 		lock.lock()
        if (BIO_write(readBio, data.toCValues(), data.size) != data.size) {
            // FIXME: throw error!!
            log.warning("could not write data to BIO buffer")
        }

        log.finest("SSLEngine decryption state: " + state.name)

        when (state) {
            State.handshaking -> doHandshaking()
            State.active -> readDataFromNetwork()
            else -> {}
        }
    }

    fun encrypt(data: ByteArray) {
        if (!data.isEmpty()) {
            awaitingEncryptionLock.lock()
            awaitingEncryption += data
            awaitingEncryptionLock.unlock()
        }

        when (state) {
            State.handshaking -> doHandshaking()
            State.active -> encryptWaiting()
            State.closed -> {}
        }
    }

    private fun doHandshaking() {
        val result = HandshakeResult.from(code = SSL_do_handshake(ssl), engine = this)
        when (result) {
            HandshakeResult.incomplete -> {
                state = State.handshaking
                writeDataToNetwork()
            }

            HandshakeResult.complete -> {
                if (!isPeerCertificateValid()) {
                    // FIXME: throw error!!
                    log.warning("peer certificate is invalid!")
                }
                state = State.active
                readDataFromNetwork()
                writeDataToNetwork()
                encryptWaiting()
            }

            HandshakeResult.failed -> {
                state = State.closed
                writeDataToNetwork()
            }
        }
    }

    private fun readDataFromNetwork() {
        var n = 0
        do {
            memScoped {
                val buffer = allocArray<ByteVar>(4096)
                log.finest("SSLEngine reading to buffer...")
                n = SSL_read(ssl, buffer, 4096)
                if (n > 0) {
                    val data = buffer.readBytes(n)
                    log.finest("SSLEngine read " + data.size + " of decrypted data...")
                    // dispatch_async(dataConsumerDispatchQueue) {
                    // println("SSLEngine passing " + data.size  + " bytes to connector...")
                    connector.process(data)
                    // }
                }
            }
        } while (n > 0)

        val status = SSLStatus.from(code = n, engine = this)
        log.finest("SSLEngine status code: " + n + ", status: " + status.name)
        when (status) {
            SSLStatus.want_write -> writeDataToNetwork()
            SSLStatus.want_read, SSLStatus.ok -> {}
            SSLStatus.fail -> {
                state = State.closed
                writeDataToNetwork()
            }
        }
    }

// 	private val socketWriterDispatchQueue = dispatch_queue_create("socketWriter", null);
	
    private fun writeDataToNetwork() {
        var n = 0
        do {
            val waiting = BIO_ctrl_pending(writeBio).toInt()
            log.finest("sending $waiting bytes..")
            memScoped {
                val buffer = allocArray<ByteVar>(waiting)
                n = BIO_read(writeBio, buffer, waiting)
                if (n > 0) {
                    val data = buffer.readBytes(n)
                    // dispatch_async(socketWriterDispatchQueue) {
                    connector.writeDataToSocket(data)
                    // }
                }
            }
        } while (n > 0)
    }

    private var awaitingEncryption: MutableList<ByteArray> = mutableListOf()
    private val awaitingEncryptionLock = NSLock()

    private fun encryptWaiting() {
        awaitingEncryptionLock.lock()
        if (awaitingEncryption.isEmpty()) {
            awaitingEncryptionLock.unlock()
            writeDataToNetwork()
            return
        }

        log.finest("encrypting waiting data..")
        var shouldContinue = true
        while (shouldContinue) {
            shouldContinue = false
            val data = awaitingEncryption.firstOrNull()
            log.finest("encrypting ${data?.toKString()}")
            data?.let {
                val n = SSL_write(ssl, it.toCValues(), it.size)
                when (SSLStatus.from(code = n, engine = this)) {
                    SSLStatus.want_write, SSLStatus.ok -> {
                        writeDataToNetwork()
                        shouldContinue = true
                    }

                    SSLStatus.want_read -> {
                        shouldContinue = false
                    }

                    SSLStatus.fail -> {
                        shouldContinue = false
                        state = State.closed
                        writeDataToNetwork()
                    }
                }
                awaitingEncryption.removeAt(0)
            }
        }
        log.finest("encryption of waiting data finished")
        awaitingEncryptionLock.unlock()
    }

    private fun peerCertificateChain(): CFArrayRef? {
        val chain = SSL_get_peer_cert_chain(ssl) ?: return null
        var list: List<SecCertificateRef> = emptyList()

        var cert: CPointer<X509>?
        do {
            cert = sk_X509_shift(chain)
            if (cert != null) {
                var buffer = cValue<CPointerVar<UByteVar>>()
                val len = i2d_X509(cert, buffer)
                if (len > 0) {
                    val bytes = buffer.getBytes()
                        .toUByteArray()
                    val data = CFDataCreate(null, bytes.toCValues(), buffer.size.toLong())
                    SecCertificateCreateWithData(null, data)?.let { secCert ->
                        list += secCert
                    }
                    CFRelease(data)
                }
            }
        } while (cert != null)

        val array = CFArrayCreateMutable(kCFAllocatorDefault, list.size.toLong(), null)
        for (i in 1..list.size) {
            CFArraySetValueAtIndex(array, (i - 1).toLong(), list.get(i - 1))
        }
        return array
    }

    private fun isPeerCertificateValid(): Boolean {
        var isValid = false
        peerCertificateChain()?.let { array ->
            var trustRef = cValue<SecTrustRefVar>()
            if (SecTrustCreateWithCertificates(array, SecPolicyCreateBasicX509(), trustRef) !=
                errSecSuccess
            ) {
                return false
            }

            val domainArr = domain.encodeToByteArray()
                .toUByteArray()
            val cfdomain = CFStringCreateWithBytes(
                null,
                domainArr.toCValues(),
                domainArr.size.toLong(),
                kCFStringEncodingUTF8,
                isExternalRepresentation = false
            )
            val policy = SecPolicyCreateSSL(false, cfdomain)

            memScoped {
                val trust = trustRef.getPointer(MemScope()).pointed.value!!
                SecTrustSetPolicies(trust, policy)
                var resultRef = cValue<SecTrustResultTypeVar>()
                // var result: SecTrustResultType = kSecTrustResultUnspecified;
                SecTrustEvaluateWithError(trust, null)
                SecTrustGetTrustResult(trust, resultRef)
                val result = resultRef.getPointer(MemScope()).pointed.value
                isValid = (result == kSecTrustResultProceed || result == kSecTrustResultUnspecified)
            }
            CFRelease(cfdomain)
        }
        return isValid
    }
}
