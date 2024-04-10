package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.stanzas.Message

actual fun Bundle.getRandomPreKeyBundle(): PreKeyBundle {
    TODO("Not implemented yet")
}

actual interface CiphertextMessage {
    
}

actual class IdentityKey actual constructor(publicKey: ECPublicKey) {
    actual fun serialize(): ByteArray {
        TODO("Not yet implemented")
    }

    actual constructor(byteArray: ByteArray, offset: Int) : this(ECPublicKeyImpl()) {
        TODO("Not yet implemented")
    }

}

actual class IdentityKeyPair {
    actual fun getPublicKey(): IdentityKey {
        TODO("Not yet implemented")
    }

}

actual class SignedPreKeyRecord {
    actual fun getId(): Int {
        TODO("Not yet implemented")
    }

    actual fun getKeyPair(): ECKeyPair {
        TODO("Not yet implemented")
    }

    actual fun getSignature(): ByteArray {
        TODO("Not yet implemented")
    }

}

actual class ECKeyPair {
    actual fun getPublicKey(): ECPublicKey {
        TODO("Not yet implemented")
    }

}

actual class PreKeyRecord {
    actual fun getId(): Int {
        TODO("Not yet implemented")
    }

    actual fun getKeyPair(): ECKeyPair {
        TODO("Not yet implemented")
    }

}

actual object OMEMOEncryptor {
    actual fun decrypt(
        store: SignalProtocolStore,
        session: OMEMOSession,
        stanza: Message
    ): Message {
        TODO("Not yet implemented")
    }

    actual fun encrypt(
        session: OMEMOSession,
        plaintext: String
    ): Element {
        TODO("Not yet implemented")
    }

}

actual interface ECPublicKey {
    actual fun serialize(): ByteArray
}

class ECPublicKeyImpl: ECPublicKey {
    override fun serialize(): ByteArray {
        TODO("Not yet implemented")
    }

}

actual class PreKeyBundle {
    
}

actual class SessionBuilder actual constructor(
    store: SignalProtocolStore,
    address: SignalProtocolAddress
) {
    @Throws(InvalidKeyException::class, UntrustedIdentityException::class)
    actual fun process(preKeyBundle: PreKeyBundle) {
    }

}

actual class InvalidKeyException : Exception()

actual class UntrustedIdentityException : Exception()

actual class SessionCipher actual constructor(
    store: SignalProtocolStore,
    address: SignalProtocolAddress
) {

}

actual class SignalProtocolAddress actual constructor(name: String, deviceId: Int) {
    actual fun getName(): String {
        TODO("Not yet implemented")
    }

    actual fun getDeviceId(): Int {
        TODO("Not yet implemented")
    }

}

actual interface SignalProtocolStore {
    actual fun getIdentityKeyPair(): IdentityKeyPair
    actual fun getLocalRegistrationId(): Int;

    actual fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord
    actual fun loadPreKey(preKeyId: Int): PreKeyRecord

    actual fun containsSession(address: SignalProtocolAddress): Boolean
    actual fun getIdentity(address: SignalProtocolAddress): IdentityKey

    actual fun saveIdentity(address: SignalProtocolAddress, key: IdentityKey): Boolean
}

actual class InMemoryOMEMOSessionStore actual constructor(): OMEMOSessionStore {
    override fun getOMEMOSession(jid: BareJID): OMEMOSession? {
        TODO("Not yet implemented")
    }

    override fun storeOMEMOSession(omemoSession: OMEMOSession) {
        TODO("Not yet implemented")
    }

    override fun removeOMEMOSession(jid: BareJID) {
        TODO("Not yet implemented")
    }

}

actual abstract class InputStream {
    
}

actual abstract class OutputStream {
    
}

actual object OMEMOFileEncryptor {
    actual fun encrypt(
        input: InputStream,
        keyAndIv: ByteArray,
        output: OutputStream
    ) {
        TODO("Not yet implemented")
    }

    actual fun decrypt(
        input: InputStream,
        keyAndIv: ByteArray,
        output: OutputStream
    ) {
        TODO("Not yet implemented")
    }

    actual fun cipherOutputStream(
        keyAndIv: ByteArray,
        output: OutputStream
    ): OutputStream {
        TODO("Not yet implemented")
    }

    actual fun cipherInputStream(
        keyAndIv: ByteArray,
        input: InputStream
    ): InputStream {
        TODO("Not yet implemented")
    }

}