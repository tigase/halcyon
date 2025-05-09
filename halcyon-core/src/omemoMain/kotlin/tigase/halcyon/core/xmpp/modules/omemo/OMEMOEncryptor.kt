package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.stanzas.Message

expect object OMEMOEncryptor {

    fun decrypt(
        store: SignalProtocolStore,
        session: OMEMOSession,
        stanza: Message,
        healSession: (SignalProtocolAddress) -> Unit
    ): OMEMOMessage
    fun encrypt(session: OMEMOSession, plaintext: String?): Element
}
