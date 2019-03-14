package tigase.halcyon.core.xmpp

import kotlin.jvm.JvmStatic

data class JID(val bareJID: BareJID, val resource: String?) {

	constructor(localpart: String? = null, domain: String, resource: String? = null) : this(
		BareJID(localpart, domain), resource
	)

	val domain
		get() = bareJID.domain

	val localpart
		get() = bareJID.localpart

	override fun toString(): String = bareJID.toString() + if (resource != null) "/$resource" else ""

	companion object {
		@JvmStatic
		fun parse(jid: String): JID {
			val x = parseJID(jid)
			return JID(BareJID(x[0], x[1]!!), x[2])
		}
	}
}

fun String.toJID(): JID = JID.parse(this)