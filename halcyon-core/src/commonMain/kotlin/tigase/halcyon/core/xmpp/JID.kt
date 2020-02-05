/*
 * Tigase Halcyon XMPP Library
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

import kotlinx.serialization.*
import kotlinx.serialization.internal.StringDescriptor
import kotlin.jvm.JvmStatic

@Serializable
data class JID(val bareJID: BareJID, val resource: String?) {

	constructor(localpart: String? = null, domain: String, resource: String? = null) : this(
		BareJID(localpart, domain), resource
	)

	val domain
		get() = bareJID.domain

	val localpart
		get() = bareJID.localpart

	override fun toString(): String = bareJID.toString() + if (resource != null) "/$resource" else ""

	@Serializer(forClass = JID::class)
	companion object : KSerializer<JID> {

		@JvmStatic
		fun parse(jid: String): JID {
			val x = parseJID(jid)
			return JID(BareJID(x[0], x[1]!!), x[2])
		}

		override val descriptor: SerialDescriptor = StringDescriptor.withName("JID")
		override fun serialize(encoder: Encoder, obj: JID) = encoder.encodeString(obj.toString())
		override fun deserialize(decoder: Decoder): JID = JID.parse(decoder.decodeString())
	}
}

fun String.toJID(): JID = JID.parse(this)