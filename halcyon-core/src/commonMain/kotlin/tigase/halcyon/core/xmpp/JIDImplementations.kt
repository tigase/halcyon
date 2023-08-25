/*
 * halcyon-core
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core.xmpp

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmStatic

@Serializable(with = BareJIDSerializer::class)
class BareJID(override val localpart: String? = null, override val domain: String) : JID {

	override fun toString(): String {
		return (when {
			localpart != null -> "$localpart@$domain"
			else -> domain
		})
	}

	override fun equals(other: Any?): Boolean = equalsJID(this, other)

	override fun hashCode(): Int = arrayOf(localpart, domain).contentHashCode()

}


@Serializable(with = FullJIDSerializer::class)
class FullJID(val bareJID: BareJID, val resource: String?) : JID {

	constructor(localpart: String? = null, domain: String, resource: String? = null) : this(
		BareJID(localpart, domain), resource
	)

	override val domain
		get() = bareJID.domain

	override val localpart
		get() = bareJID.localpart

	override fun toString(): String = bareJID.toString() + if (resource != null) "/$resource" else ""

	override fun equals(other: Any?): Boolean = equalsJID(this, other)

	override fun hashCode(): Int = arrayOf(localpart, domain).contentHashCode()

}

internal fun equalsJID(jid: JID, other: Any?): Boolean {
	return if (jid === other) {
		true
	} else if (jid is FullJID && other is FullJID) {
		jid.localpart == other.localpart && jid.domain == other.domain && jid.resource == other.resource
	} else if (jid is BareJID && other is BareJID) {
		jid.localpart == other.localpart && jid.domain == other.domain
	} else if (jid is FullJID && other is BareJID) {
		jid.localpart == other.localpart && jid.domain == other.domain
	} else if (jid is BareJID && other is FullJID) {
		jid.localpart == other.localpart && jid.domain == other.domain
	} else false
}
