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
package tigase.halcyon.core.xmpp.stanzas

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import tigase.halcyon.core.parseISO8601
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.MessageStanzaSerializer
import tigase.halcyon.core.xml.attributeProp
import tigase.halcyon.core.xml.stringElementProperty
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

@Serializable
enum class MessageType(val value: String) {
    Chat("chat"),
    Error("error"),
    Groupchat("groupchat"),
    Headline("headline"),
    Normal("normal")
}

@Serializable(with = MessageStanzaSerializer::class)
open class Message(wrappedElement: Element) : Stanza<MessageType?>(wrappedElement) {

    init {
        require(wrappedElement.name == NAME) { "Message stanza requires element $NAME." }
    }

    companion object {

        const val NAME = "message"
    }

    override var type: MessageType? by attributeProp(
        valueToString = { v -> v?.value },
        stringToValue = { s: String? ->
            s?.let {
                MessageType.values()
                    .firstOrNull { te -> te.value == it } ?: throw XMPPException(
                    ErrorCondition.BadRequest,
                    "Unknown stanza type '$it'"
                )
            }
        }
    )

    var body: String? by stringElementProperty()
}

fun Message.getTimestampOrNull(): Instant? = this.getChildrenNS("delay", "urn:xmpp:delay")
    ?.let {
        it.attributes["stamp"]?.let { stamp -> parseISO8601(stamp) }
    }
