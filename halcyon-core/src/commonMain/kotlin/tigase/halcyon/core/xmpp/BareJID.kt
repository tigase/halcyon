package tigase.halcyon.core.xmpp

import kotlin.jvm.JvmStatic

data class BareJID constructor(val localpart: String? = null, val domain: String) {

	override fun toString(): String {
		return (when {
			localpart != null -> "$localpart@$domain"
			else -> domain
		})
	}

	companion object {
		@JvmStatic
		fun parse(jid: String): BareJID {
			val x = parseJID(jid)
			return BareJID(x[0], x[1]!!)
		}
	}
}

internal fun parseJID(jid: String): Array<String?> {
	val result = arrayOfNulls<String>(3)

	// Cut off the resource part first
	var idx = jid.indexOf('/')

	// Resource part:
	result[2] = if (idx == -1) null else jid.substring(idx + 1)

	val id = if (idx == -1) jid else jid.substring(0, idx)

	// Parse the localpart and the domain name
	idx = id.indexOf('@')
	result[0] = if (idx == -1) null else id.substring(0, idx)
	result[1] = if (idx == -1) id else id.substring(idx + 1)

	return result
}

fun String.toBareJID(): BareJID = BareJID.parse(this)