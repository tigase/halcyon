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
package tigase.halcyon.core.xmpp.stanzas

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.getChildContent
import tigase.halcyon.core.xml.setAtt
import tigase.halcyon.core.xml.setChildContent
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

/**
 * Availability sub-state
 */
enum class Show(val weight: Int, val value: String) {

	/**
	 * The entity or resource is actively interested in chatting.
	 */
	Chat(5, "chat"),

	/**
	 * The entity or resource is temporarily away.
	 */
	Away(3, "away"),

	/**
	 * The entity or resource is away for an extended period (xa =
	 * "eXtended Away").
	 */
	XA(2, "xa"),

	/**
	 * The entity or resource is busy (dnd = "Do Not Disturb").
	 */
	DnD(1, "dnd"),

}

enum class PresenceType(val value: String) { Error("error"),
	Probe("probe"),
	Subscribe("subscribe"),
	Subscribed("subscribed"),
	Unavailable("unavailable"),
	Unsubscribe("unsubscribe"),
	Unsubscribed("unsubscribed"),
}

class Presence(wrappedElement: Element) : Stanza<PresenceType?>(wrappedElement) {

	companion object {

		const val NAME = "presence"
	}

	override var type: PresenceType?
		set(value) = setAtt("type", value?.value)
		get() = attributes["type"]?.let {
			PresenceType.values().firstOrNull { te -> te.value == it } ?: throw XMPPException(
				ErrorCondition.BadRequest, "Unknown stanza type '$it'"
			)
		}

	private fun getShowValue(): Show? {
		return getChildContent("show")?.let {
			Show.values().firstOrNull { s -> s.value == it } ?: throw XMPPException(
				ErrorCondition.BadRequest, "Unknown show value: '$it'"
			)
		}
	}

	var show: Show?
		set(value) = setChildContent("show", value?.value)
		get() = getShowValue()

	var priority: Int
		set(value) = setChildContent("priority", value.toString())
		get() = getChildContent("priority", "0")!!.toInt()

	var status: String?
		set(value) = setChildContent("status", value)
		get() = getChildContent("status")

}
