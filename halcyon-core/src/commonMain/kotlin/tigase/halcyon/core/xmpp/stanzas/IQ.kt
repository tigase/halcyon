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

import kotlinx.serialization.Serializable
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.IQStanzaSerializer
import tigase.halcyon.core.xml.attributeProp
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

@Serializable
enum class IQType(val value: String) {
    Error("error"),
    Get("get"),
    Result("result"),
    Set("set")
}

@Serializable(with = IQStanzaSerializer::class)
class IQ(wrappedElement: Element) : Stanza<IQType>(wrappedElement) {

    init {
        require(wrappedElement.name == NAME) { "IQ stanza requires element $NAME." }
    }

    companion object {

        const val NAME = "iq"
    }

    override var type: IQType by attributeProp(stringToValue = { v ->
        v?.let {
            IQType.values()
                .firstOrNull { te -> te.value == it }
        } ?: throw XMPPException(ErrorCondition.BadRequest, "Unknown stanza type '$v'")
    }, valueToString = { v -> v.value })
}
