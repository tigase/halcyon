package tigase.halcyon.core.xmpp.modules.omemo

expect class SessionCipher(store: SignalProtocolStore, address: SignalProtocolAddress) {

//    fun decrypt(encryptedMessage: CiphertextMessage): ByteArray
//    fun encrypt(data: ByteArray): CiphertextMessage
}
