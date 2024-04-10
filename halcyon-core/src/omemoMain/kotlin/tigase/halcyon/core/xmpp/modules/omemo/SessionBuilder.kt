package tigase.halcyon.core.xmpp.modules.omemo

expect class SessionBuilder(store: SignalProtocolStore, address: SignalProtocolAddress) {

    @Throws(InvalidKeyException::class, UntrustedIdentityException::class)
    fun process(preKeyBundle: PreKeyBundle)
}

expect class InvalidKeyException: Exception {}
expect class UntrustedIdentityException: Exception {}
