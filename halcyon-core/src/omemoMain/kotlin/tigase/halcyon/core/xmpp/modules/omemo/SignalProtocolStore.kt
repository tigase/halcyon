package tigase.halcyon.core.xmpp.modules.omemo

expect interface SignalProtocolStore {

    fun getIdentityKeyPair(): IdentityKeyPair
    fun getLocalRegistrationId(): Int;

    fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord
    fun loadPreKey(preKeyId: Int): PreKeyRecord

    fun containsSession(address: SignalProtocolAddress): Boolean
    fun getIdentity(address: SignalProtocolAddress): IdentityKey

    fun saveIdentity(address: SignalProtocolAddress, key: IdentityKey): Boolean

}

expect interface ECPublicKey {
    fun serialize(): ByteArray
}

expect class IdentityKey(publicKey: ECPublicKey) {
    constructor(byteArray: ByteArray, offset: Int)

    fun serialize(): ByteArray
}