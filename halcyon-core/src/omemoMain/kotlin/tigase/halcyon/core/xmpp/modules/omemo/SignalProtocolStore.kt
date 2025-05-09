package tigase.halcyon.core.xmpp.modules.omemo

// expect interface SignalProtocolStore {
//
//    fun getIdentityKeyPair(): IdentityKeyPair
//    fun getLocalRegistrationId(): Int;
//
//    fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord
//    fun loadPreKey(preKeyId: Int): PreKeyRecord
//    fun storePreKey(preKeyId: Int, record: PreKeyRecord);
//    fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord);
//
//    fun containsSession(address: SignalProtocolAddress): Boolean
//    fun getIdentity(address: SignalProtocolAddress): IdentityKey?
//
//    fun saveIdentity(address: SignalProtocolAddress, key: IdentityKey): Boolean
//
// }

expect interface SignalProtocolStore :
    IdentityKeyStore,
    PreKeyStore,
    SessionStore,
    SignedPreKeyStore

expect interface ECPublicKey {
    fun serialize(): ByteArray
}

expect class IdentityKey(publicKey: ECPublicKey) {
    constructor(byteArray: ByteArray, offset: Int)

    fun getFingerprint(): String
    fun getPublicKey(): ECPublicKey

    fun serialize(): ByteArray
}
