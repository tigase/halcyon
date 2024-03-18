package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.xmpp.BareJID
import java.util.concurrent.ConcurrentHashMap

/**
 *
 */
interface OMEMOSessionStore {

    fun getOMEMOSession(jid: BareJID): OMEMOSession?

    fun storeOMEMOSession(omemoSession: OMEMOSession)

    fun removeOMEMOSession(jid: BareJID)

}

class InMemoryOMEMOSessionStore : OMEMOSessionStore {

    private val sessions: MutableMap<BareJID, OMEMOSession> = ConcurrentHashMap<BareJID, OMEMOSession>()
    override fun getOMEMOSession(jid: BareJID): OMEMOSession? = sessions[jid]

    override fun storeOMEMOSession(omemoSession: OMEMOSession) {
        sessions[omemoSession.remoteJid] = omemoSession
    }

    override fun removeOMEMOSession(jid: BareJID) {
        sessions.remove(jid)
    }

}