package tigase.halcyon.core.xmpp.modules.omemo

expect class SessionBuilder(store: SignalProtocolStore, address: SignalProtocolAddress) {

}

@Throws(InvalidKeyException::class, UntrustedIdentityException::class)
expect fun SessionBuilder.processPreKeyBundle(preKeyBundle: PreKeyBundle)

class InvalidKeyException(message: String?) : Exception(message)
class UntrustedIdentityException(message: String?) : Exception(message)
