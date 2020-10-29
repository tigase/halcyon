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
package tigase.halcyon.core.requests

import getFromAttr
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xml.Element

class RequestsManager {

	private val log = LoggerFactory.logger("tigase.halcyon.core.requests.RequestsManager")

	private val executor = tigase.halcyon.core.excutor.Executor()

	private val requests = HashMap<String, AbstractRequest<*, *>>()

	internal fun register(request: AbstractRequest<*, *>) {
		requests[key(request.stanza)] = request
	}

	private fun key(element: Element): String = "${element.name}:${element.attributes["id"]}"

	@Suppress("UNCHECKED_CAST")
	fun getRequest(response: Element): AbstractRequest<*, *>? {
		val id = key(response)
//		val from = response.attributes["from"]

		val request = requests[id] ?: return null

		if (verify(request, response)) {
			requests.remove(id)
			return request
		} else {
			return null
		}
	}

	private fun verify(entry: AbstractRequest<*, *>, response: Element): Boolean {
		val jid = response.getFromAttr()

		if (jid != null && entry.jid != null && jid.bareJID == entry.jid.bareJID) {
			return true
		} else if (entry.jid == null && jid == null) {
			return true
		}
		// TODO
//		else {
//			val userJID = sessionObject.getProperty(ResourceBinderModule.BINDED_RESOURCE_JID)
//			if (entry.jid == null && userJID != null && jid.bareJID.equals(userJID!!.bareJId)) {
//				return true
//			}
//		}
		return false
	}

	fun findAndExecute(response: Element): Boolean {
		val r: AbstractRequest<*, *> = getRequest(response) ?: return false
		execute { r.setResponseStanza(response) }
		return true
	}

	private fun execute(runnable: () -> Unit) {
		executor.execute {
			try {
				runnable.invoke()
			} catch (e: Throwable) {
				log.warning(e) { "Error on processing response" }
			}
		}
	}

	fun timeoutAll(maxCreationTimestamp: Long = Long.MAX_VALUE) {
		log.info { "Timeout all waiting requests" }
		val iterator = requests.entries.iterator()
		while (iterator.hasNext()) {
			val request = iterator.next().value
			if (request.creationTimestamp < maxCreationTimestamp) {
				iterator.remove()
				if (!request.isCompleted) execute { request.markTimeout() }
			}
		}
	}

	fun findOutdated() {
		val now = currentTimestamp()

		requests.values.filter { request -> request.isCompleted || request.creationTimestamp + request.timeoutDelay <= now }
			.forEach {
				requests.remove(it.id)
				if (it.creationTimestamp + it.timeoutDelay <= now) {
					execute { it.markTimeout() }
				}
			}
	}

	fun getWaitingRequestsSize(): Int = requests.size

}