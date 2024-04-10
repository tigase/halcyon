package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.xmpp.BareJID

/**
 *
 */
interface OMEMOSessionStore {

    fun getOMEMOSession(jid: BareJID): OMEMOSession?

    fun storeOMEMOSession(omemoSession: OMEMOSession)

    fun removeOMEMOSession(jid: BareJID)

}

expect class InMemoryOMEMOSessionStore() : OMEMOSessionStore {

}