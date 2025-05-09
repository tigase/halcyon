package tigase.halcyon.core.xmpp.modules.omemo

import org.whispersystems.libsignal.ecc.Curve

actual fun Bundle.getRandomPreKeyBundle(): PreKeyBundle {
    val preKey = this.preKeys.random()
    return PreKeyBundle(
        this.deviceId,
        this.deviceId,
        preKey.preKeyId,
        Curve.decodePoint(preKey.preKeyPublic, 0),
        this.signedPreKeyId,
        Curve.decodePoint(this.signedPreKeyPublic, 0),
        this.signedPreKeySignature,
        IdentityKey(this.identityKey, 0)
    )
}

actual typealias PreKeyBundle = org.whispersystems.libsignal.state.PreKeyBundle

actual typealias SignalProtocolAddress = org.whispersystems.libsignal.SignalProtocolAddress

actual typealias ECPublicKey = org.whispersystems.libsignal.ecc.ECPublicKey
actual typealias ECKeyPair = org.whispersystems.libsignal.ecc.ECKeyPair
actual typealias IdentityKey = org.whispersystems.libsignal.IdentityKey

actual typealias IdentityKeyPair = org.whispersystems.libsignal.IdentityKeyPair
actual typealias SignedPreKeyRecord = org.whispersystems.libsignal.state.SignedPreKeyRecord
actual typealias PreKeyRecord = org.whispersystems.libsignal.state.PreKeyRecord
actual class SessionBuilder actual constructor(
    store: SignalProtocolStore,
    address: SignalProtocolAddress
) : org.whispersystems.libsignal.SessionBuilder(store, address)

actual typealias SessionCipher = org.whispersystems.libsignal.SessionCipher

actual typealias UntrustedIdentityException = org.whispersystems.libsignal.UntrustedIdentityException
actual typealias InvalidKeyException = org.whispersystems.libsignal.InvalidKeyException

actual typealias SignalProtocolStore = org.whispersystems.libsignal.state.SignalProtocolStore

actual typealias InvalidKeyIdException = org.whispersystems.libsignal.InvalidKeyIdException
actual typealias PreKeyStore = org.whispersystems.libsignal.state.PreKeyStore

actual typealias SignedPreKeyStore = org.whispersystems.libsignal.state.SignedPreKeyStore

actual typealias SessionRecord = org.whispersystems.libsignal.state.SessionRecord

actual typealias SessionStore = org.whispersystems.libsignal.state.SessionStore

actual typealias IdentityKeyStore = org.whispersystems.libsignal.state.IdentityKeyStore

actual typealias IdentityKeyStoreDirection = org.whispersystems.libsignal.state.IdentityKeyStore.Direction
