package org.tigase.jaxmpp.core.requests

import getFromAttr
import getIdAttr
import getToAttr
import org.tigase.jaxmpp.core.currentTimestamp
import org.tigase.jaxmpp.core.exceptions.JaXMPPException
import org.tigase.jaxmpp.core.excutor.Executor
import org.tigase.jaxmpp.core.logger.Level
import org.tigase.jaxmpp.core.logger.Logger
import org.tigase.jaxmpp.core.xml.Element

class RequestsManager {

	private val log = Logger("org.tigase.jaxmpp.core.requests.RequestsManager")

	private val executor = Executor()

	private val requests = HashMap<String, Request>()

	fun create(element: Element): Request {
		val id = element.getIdAttr() ?: throw JaXMPPException("Stanza must contains 'id' attribute")
		val jid = element.getToAttr()

		val request = Request(jid, id, currentTimestamp(), element)
		requests[id] = request
		return request
	}

	fun getRequest(response: Element): Request? {
		val id = response.attributes["id"] ?: return null
		val from = response.attributes["from"]

		val request = requests[id] ?: return null

		if (!verify(request, response)) return null

		requests.remove(id)
		return request
	}

	private fun verify(entry: Request, response: Element): Boolean {
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
		var r = getRequest(response) ?: return false
		executor.execute {
			try {
				r.responseStanza = response
			} catch (e: Throwable) {
				log.log(Level.WARNING, "Error on processing response", e)
			}
		}
		return true
	}

	fun findOutdated() {
		val now = currentTimestamp()
		val iterator = requests.entries.iterator()
		while (iterator.hasNext()) {
			val request = iterator.next().value;
			if (request.creationTimestamp + request.timeoutDelay <= now) {
				iterator.remove()
				try {
					request.callTimeout()
				} catch (e: Exception) {
					log.log(Level.WARNING, "Problem on calling timeout on request " + request.id, e)
				}
			}
		}
	}

}