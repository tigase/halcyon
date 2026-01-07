package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.xmpp.BareJID

/**
 * OMEMO session.
 * @param localRegistrationId local registration id.
 * @param localJid own (local) JabberID.
 * @param remoteJid JabberID of the other party.
 * @param ciphers map of ciphers assigned with addresses.
 */
data class OMEMOSession(
    val localRegistrationId: Int,
    val localJid: BareJID,
    val ciphers: MutableMap<SignalProtocolAddress, SessionCipher>
)

fun createSession(store: SignalProtocolStore, bundle: Bundle): Result<Unit> {
    try {
        val address = SignalProtocolAddress(bundle.jid.toString(), bundle.deviceId)
        SessionBuilder(store, address).processPreKeyBundle(bundle.getRandomPreKeyBundle())
        return Result.success(Unit);
    } catch (e: Exception) {
        return Result.failure(e)
    }
}

fun createCiphers(store: SignalProtocolStore, bundles: List<Bundle>): Map<SignalProtocolAddress, SessionCipher> {
    return bundles.mapNotNull { bundle ->
        val addr = SignalProtocolAddress(bundle.jid.toString(), bundle.deviceId)
        val cipher = buildCipher(store, addr, bundle)
        if (cipher == null) null else
            addr to cipher
    }.toMap()
}

fun buildCipher(store: SignalProtocolStore, address: SignalProtocolAddress, bundle: Bundle): SessionCipher? {
    try {
        if (!store.containsSession(address)) {
            val sessionBuilder = SessionBuilder(store, address)
            sessionBuilder.processPreKeyBundle(bundle.getRandomPreKeyBundle())
        }
        return SessionCipher(store, address)
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}