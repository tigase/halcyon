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

import OpenSSL.*
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Security.*
import platform.darwin.ByteVar
import tigase.halcyon.core.logger.LoggerFactory

class SSLEngine(connector: SocketConnector, domain: String) {

    private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.SSLEngine")
    
    enum class HandshakeResult {
        complete,
        incomplete,
        failed;

        companion object {
            fun from(code: Int, engine: SSLEngine): HandshakeResult {
                if (code == 1) {
                    return complete;
                }

                val status = SSLStatus.from(code = code, engine = engine);
                return when (status) {
                    SSLStatus.want_read, SSLStatus.want_write -> incomplete;
                    else -> failed;
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
            fun from(code: Int, engine: SSLEngine): SSLStatus {
                if (code == 0) {
                    return ok;
                }

                val status = SSL_get_error(engine.ssl, code);
                return when (status) {
                    SSL_ERROR_NONE -> ok;
                    SSL_ERROR_WANT_READ -> want_read;
                    SSL_ERROR_WANT_WRITE -> want_write;
                    else -> fail;
                }
            }
        }
    }
    
    private val connector: SocketConnector = connector;
    private val domain = domain;
    private var writeBio: CPointer<BIO>?
    private var readBio: CPointer<BIO>?
    private var ctx: CPointer<SSL_CTX>?;
    private var ssl: CPointer<SSL>?;
    private var state: State = State.handshaking;

    init {
        ctx = SSL_CTX_new(TLS_client_method());
        SSL_CTX_ctrl(ctx, SSL_CTRL_SET_SESS_CACHE_MODE, (SSL_SESS_CACHE_CLIENT or SSL_SESS_CACHE_NO_INTERNAL_STORE).toLong(), null);
        ssl = SSL_new(ctx);
        readBio = BIO_new(BIO_s_mem());
        writeBio = BIO_new(BIO_s_mem())
        SSL_set_bio(ssl, readBio, writeBio);
        SSL_set_connect_state(ssl);
    }

    fun decrypt(data: ByteArray) {
        if (BIO_write(readBio, data.toCValues(), data.size) != data.size) {
            // FIXME: throw error!!
            log.finest("could not write data to BIO buffer");
        }

        when (state) {
            State.handshaking -> doHandshaking();
            State.active -> readDataFromNetwork();
        }
    }

    fun encrypt(data: ByteArray) {
        if (!data.isEmpty()) {
            awaitingEncryption += data;
        }

        when (state) {
            State.handshaking -> doHandshaking();
            State.active -> encryptWaiting();
            State.closed -> {}
        }
    }

    private fun doHandshaking() {
        val result = HandshakeResult.from(code = SSL_do_handshake(ssl), engine = this);
        when (result) {
            HandshakeResult.incomplete -> {
                state = State.handshaking;
                writeDataToNetwork();
            }
            HandshakeResult.complete -> {
                if (!isPeerCertificateValid()) {
                    // FIXME: throw error!!
                    log.warning("peer certificate is invalid!");
                }
                state = State.active;
                readDataFromNetwork();
                writeDataToNetwork();
                encryptWaiting();
            }
            HandshakeResult.failed -> {
                state = State.closed;
                writeDataToNetwork();
            }
        }
    }

    private fun readDataFromNetwork() {
        var n = 0;
        do {
            memScoped {
                val buffer = allocArray<ByteVar>(2048);
                n = SSL_read(ssl, buffer, 2048);
                if (n > 0) {
                    connector.process(buffer.readBytes(n));
                }
            }
        } while (n > 0);

        when (SSLStatus.from(code = n, engine = this)) {
            SSLStatus.want_write -> writeDataToNetwork();
            SSLStatus.want_read, SSLStatus.ok -> {}
            SSLStatus.fail -> {
                state = State.closed;
                writeDataToNetwork();
            }
        }
    }

    private fun writeDataToNetwork() {
        var n = 0;
        do {
            val waiting = BIO_ctrl_pending(writeBio).toInt();
            log.finest("sending ${waiting} bytes..")
            memScoped {
                val buffer = allocArray<ByteVar>(2048);
                n = BIO_read(writeBio, buffer, waiting);
                if (n > 0) {
                    connector.writeDataToSocket(buffer.readBytes(n));
                }
            }
        } while (n > 0);
    }

    private var awaitingEncryption: MutableList<ByteArray> = mutableListOf();

    private fun encryptWaiting() {
        if (awaitingEncryption.isEmpty()) {
            writeDataToNetwork();
            return;
        }

        log.finest("encrypting waiting data..")
        var shouldContinue = true;
        while (shouldContinue) {
            shouldContinue = false;
            val data = awaitingEncryption.firstOrNull();
            log.finest("encrypting ${data?.toKString()}")
            data?.let {
                val n = SSL_write(ssl, it.toCValues(), it.size);
                when (SSLStatus.from(code = n, engine = this)) {
                    SSLStatus.want_write, SSLStatus.ok -> {
                        writeDataToNetwork();
                        shouldContinue = true;
                    }
                    SSLStatus.want_read -> {
                        shouldContinue = false;
                    }
                    SSLStatus.fail -> {
                        shouldContinue = false;
                        state = State.closed;
                        writeDataToNetwork();
                    }
                }
                awaitingEncryption.removeAt(0);
            }
        }
        log.finest("encryption of waiting data finished");
    }

    private fun peerCertificateChain(): CFArrayRef? {
        val chain = SSL_get_peer_cert_chain(ssl) ?: return null;
        var list: List<SecCertificateRef> = emptyList();


        var cert: CPointer<X509>?;
        do {
            cert = sk_X509_shift(chain);
            if (cert != null) {
                var buffer = cValue<CPointerVar<UByteVar>>();
                val len = i2d_X509(cert, buffer);
                if (len > 0) {
                    val bytes = buffer.getBytes().toUByteArray();
                    val data = CFDataCreate(null, bytes.toCValues(), buffer.size.toLong());
                    SecCertificateCreateWithData(null, data)?.let { secCert ->
                        list += secCert;
                    }
                    CFRelease(data);
                }
            }
        } while (cert != null)

        val array = CFArrayCreateMutable(kCFAllocatorDefault, list.size.toLong(), null);
        for (i in 1..list.size) {
            CFArraySetValueAtIndex(array, (i-1).toLong(), list.get(i-1));
        }
        return array;
    }

    private fun isPeerCertificateValid(): Boolean {
        var isValid = false;
        peerCertificateChain()?.let { array ->
            var trustRef = cValue<SecTrustRefVar>();
            if (SecTrustCreateWithCertificates(array, SecPolicyCreateBasicX509(), trustRef) != errSecSuccess) {
                return false;
            }

            val domainArr = domain.encodeToByteArray().toUByteArray();
            val cfdomain = CFStringCreateWithBytes(null, domainArr.toCValues(), domainArr.size.toLong(), kCFStringEncodingUTF8, isExternalRepresentation = false);
            val policy = SecPolicyCreateSSL(false, cfdomain);

            memScoped {
                val trust = trustRef.getPointer(MemScope()).pointed.value!!;
                SecTrustSetPolicies(trust, policy);
                var resultRef = cValue<SecTrustResultTypeVar>()
                //var result: SecTrustResultType = kSecTrustResultUnspecified;
                SecTrustEvaluateWithError(trust, null);
                SecTrustGetTrustResult(trust, resultRef);
                val result = resultRef.getPointer(MemScope()).pointed.value;
                isValid = (result == kSecTrustResultProceed || result == kSecTrustResultUnspecified);
            }
            CFRelease(cfdomain);
        }
        return isValid;
    }

}