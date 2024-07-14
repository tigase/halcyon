package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.fromBase64
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.BareJID

/**
 * Pre-key data.
 */
data class PreKey(val preKeyId: Int, val preKeyPublic: ByteArray) {
    override fun toString(): String {
        return "PreKey(preKeyId=$preKeyId, preKeyPublic=${preKeyPublic.contentToString()})"
    }
}

/**
 * Key bundle data.
 */
data class Bundle(
    val jid: BareJID,
    val deviceId: Int,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: ByteArray,
    val signedPreKeySignature: ByteArray,
    val identityKey: ByteArray,
    val preKeys: List<PreKey>
) {
    override fun toString(): String {
        return "Bundle(jid=$jid, deviceId=$deviceId, signedPreKeyId=$signedPreKeyId, signedPreKeyPublic=${signedPreKeyPublic.contentToString()}, signedPreKeySignature=${signedPreKeySignature.contentToString()}, identityKey=${identityKey.contentToString()}, preKeys=$preKeys)"
    }
}

expect fun Bundle.getRandomPreKeyBundle(): PreKeyBundle;

/**
 * Converts `<bundle>` element to [Bundle].
 * @param jid JabberID of bundle owner.
 * @param deviceId device id of bundle publisher.
 */
fun Element.toBundleOf(jid: BareJID, deviceId: Int): Bundle {
    if (this.name != "bundle" || this.xmlns != OMEMOModule.XMLNS) throw HalcyonException("Not a bundle element.")
    val signedPreKeyId = this.getFirstChild("signedPreKeyPublic")?.attributes?.get("signedPreKeyId")
        ?: throw HalcyonException("Incomplete bundle: signedPreKeyId not found")
    val signedPreKeyPublic = this.getFirstChild("signedPreKeyPublic")?.value
        ?: throw HalcyonException("Incomplete bundle: signedPreKeyPublic not found")
    val signedPreKeySignature = this.getFirstChild("signedPreKeySignature")?.value
        ?: throw HalcyonException("Incomplete bundle: signedPreKeySignature not found")
    val identityKey = this.getFirstChild("identityKey")?.value
        ?: throw HalcyonException("Incomplete bundle: identityKey not found")

    val prekeys = this.getFirstChild("prekeys")
        ?.getChildren("preKeyPublic")
        ?.map {
            PreKey(
                it.attributes["preKeyId"]?.toInt() ?: throw HalcyonException("Invalid preKeyPublic"),
                it.value?.fromBase64() ?: throw HalcyonException("Invalid preKeyPublic")
            )
        } ?: emptyList()
    return Bundle(
        jid = jid,
        deviceId = deviceId,
        signedPreKeyId = signedPreKeyId.toInt(),
        signedPreKeyPublic = signedPreKeyPublic.fromBase64(),
        signedPreKeySignature = signedPreKeySignature.fromBase64(),
        identityKey = identityKey.fromBase64(),
        preKeys = prekeys
    )
}

expect interface CiphertextMessage {}

expect class IdentityKeyPair(data: ByteArray) {
//    val publicKey: IdentityKey
    fun getPublicKey(): IdentityKey
    fun serialize(): ByteArray
}

expect class SignedPreKeyRecord(data: ByteArray) {
    fun getId(): Int
    fun getKeyPair(): ECKeyPair
    fun getSignature(): ByteArray
    fun serialize(): ByteArray
}

expect class ECKeyPair {
    fun getPublicKey(): ECPublicKey
}

expect class PreKeyRecord(data: ByteArray) {
    fun getId(): Int
    fun getKeyPair(): ECKeyPair
    fun serialize(): ByteArray
}

expect class InvalidKeyIdException(message: String): Exception {}

expect interface PreKeyStore {
    @Throws(InvalidKeyIdException::class)
    fun loadPreKey(preKeyId: Int): PreKeyRecord
    fun storePreKey(preKeyId: Int, record: PreKeyRecord)
    fun containsPreKey(preKeyId: Int): Boolean
    fun removePreKey(preKeyId: Int)
}

expect interface SignedPreKeyStore {

    @Throws(InvalidKeyIdException::class)
    fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord
    fun loadSignedPreKeys(): List<SignedPreKeyRecord?>?
    fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord)
    fun containsSignedPreKey(signedPreKeyId: Int): Boolean
    fun removeSignedPreKey(signedPreKeyId: Int)
}

expect class SessionRecord(data: ByteArray) {
    fun serialize(): ByteArray
}

expect interface SessionStore {

    fun loadSession(address: SignalProtocolAddress): SessionRecord

    fun getSubDeviceSessions(name: String): List<Int>

    fun storeSession(address: SignalProtocolAddress, record: SessionRecord)

    fun containsSession(address: SignalProtocolAddress): Boolean

    fun deleteSession(address: SignalProtocolAddress)

    fun deleteAllSessions(name: String)
}

expect enum class IdentityKeyStoreDirection {}

expect interface IdentityKeyStore {
    
    fun getIdentityKeyPair(): IdentityKeyPair

    fun getLocalRegistrationId(): Int

    fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean

    fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStoreDirection
    ): Boolean


    fun getIdentity(address: SignalProtocolAddress): IdentityKey?
}

