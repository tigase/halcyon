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

/**
 * Availability sub-state
 */
enum class Show(val weight: Int) {

	/**
	 * The entity or resource is temporarily away.
	 */
	away(3),
	/**
	 * The entity or resource is actively interested in chatting.
	 */
	chat(5),
	/**
	 * The entity or resource is busy (dnd = "Do Not Disturb").
	 */
	dnd(1),
	/**
	 * The entity or resource is online and available.
	 */
	online(4),
	/**
	 * The entity or resource is offline and unavailable.
	 */
	offline(0),
	/**
	 * The entity or resource is away for an extended period (xa =
	 * "eXtended Away").
	 */
	xa(2);

}

class Presence(private val wrappedElement: Element) : Stanza(wrappedElement), Element by wrappedElement {

	companion object {
		const val NAME = "presence"
	}

}
