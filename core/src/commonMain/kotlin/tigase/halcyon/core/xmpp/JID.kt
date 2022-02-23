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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmStatic

@Serializable(with = JIDSerializer::class)
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

object JIDSerializer : KSerializer<JID> {

	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("JID", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: JID) {
		val string = value.toString()
		encoder.encodeString(string)
	}

	override fun deserialize(decoder: Decoder): JID {
		val string = decoder.decodeString()
		return JID.parse(string)
	}
}

fun String.toJID(): JID = JID.parse(this)