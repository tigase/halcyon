package tigase.halcyon.core.xmpp.modules.omemo

actual class KeyHelper {
    actual companion object {
        actual fun generateIdentityKeyPair(): IdentityKeyPair = org.whispersystems.libsignal.util.KeyHelper.generateIdentityKeyPair()
        actual fun generateRegistrationId(extendedRange: Boolean): Int = org.whispersystems.libsignal.util.KeyHelper.generateRegistrationId(extendedRange)
        actual fun generatePreKeys(start: Int, count: Int): List<PreKeyRecord> = org.whispersystems.libsignal.util.KeyHelper.generatePreKeys(start, count)
        actual fun generateSignedPreKey(identityKeyPair: IdentityKeyPair, signedPreKeyId: Int): SignedPreKeyRecord = org.whispersystems.libsignal.util.KeyHelper.generateSignedPreKey(identityKeyPair, signedPreKeyId)
    }
}
