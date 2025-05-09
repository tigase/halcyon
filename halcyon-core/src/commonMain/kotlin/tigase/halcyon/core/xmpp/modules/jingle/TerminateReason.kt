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
package tigase.halcyon.core.xmpp.modules.jingle

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element

enum class TerminateReason(val value: String) {

    AlternativeSession("alternative-session"),
    Busy("busy"),
    Cancel("cancel"),
    ConnectivityError("connectivity-error"),
    Decline("decline"),
    Expired("expired"),
    FailedApplication("failed-application"),
    FailedTransport("failed-transport"),
    GeneralError("general-error"),
    Gone("gone"),
    IncompatibleParameters("incompatible-parameters"),
    MediaError("media-error"),
    SecurityError("security-error"),
    Success("success"),
    Timeout("timeout"),
    UnsupportedApplications("unsupported-applications"),
    UnsupportedTransports("unsupported-transports");

    fun toElement(): Element = element(value) {}
    fun toReasonElement(): Element = element("reason") {
        xmlns = JingleModule.XMLNS
        addChild(toElement())
    }

    companion object {

        fun fromReasonElement(element: Element): TerminateReason? {
            if (element.name == "reason") {
                return element.children.filter {
                    it.name != "text"
                }.firstNotNullOfOrNull { fromValue(it.name) }
            }
            return null
        }
		
        fun fromValue(value: String) = entries.find { it.value == value }
    }
}
