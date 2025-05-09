package tigase.halcyon.core.xmpp.modules.omemo

expect class KeyHelper {
    companion object {
        fun generateIdentityKeyPair(): IdentityKeyPair
        fun generateRegistrationId(extendedRange: Boolean): Int
        fun generatePreKeys(start: Int, count: Int): List<PreKeyRecord>
        fun generateSignedPreKey(
            identityKeyPair: IdentityKeyPair,
            signedPreKeyId: Int
        ): SignedPreKeyRecord
    }
}
