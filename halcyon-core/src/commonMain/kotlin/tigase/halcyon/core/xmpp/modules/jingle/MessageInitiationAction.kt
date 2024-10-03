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
import tigase.halcyon.core.xmpp.modules.jingle.AbstractJingleSessionManager.Media

sealed class MessageInitiationAction(open val id: String, val actionName: String) {
	
	class Propose(override val id: String, val descriptions: List<MessageInitiationDescription>, val data: List<Element>? = null) :
		MessageInitiationAction(id, "propose") {
			val media = descriptions.map { Media.valueOf(it.media) }
		}

	class Retract(override val id: String) : MessageInitiationAction(id, "retract")

	class Accept(override val id: String) : MessageInitiationAction(id, "accept")
	class Proceed(override val id: String) : MessageInitiationAction(id, "proceed")
	class Reject(override val id: String) : MessageInitiationAction(id, "reject")

	companion object {

		fun parse(actionEl: Element): MessageInitiationAction? {
			val id = actionEl.attributes["id"] ?: return null
			when (actionEl.name) {
				"accept" -> return Accept(id)
				"proceed" -> return Proceed(id)
				"propose" -> {
					val descriptions = actionEl.children.mapNotNull { MessageInitiationDescription.parse(it) }
					if (descriptions.isNotEmpty()) {
						return Propose(id, descriptions, actionEl.children.filterNot { it.name == "description" })
					} else {
						return null
					}
				}

				"retract" -> return Retract(id)
				"reject" -> return Reject(id)
				else -> return null
			}
		}
	}
}