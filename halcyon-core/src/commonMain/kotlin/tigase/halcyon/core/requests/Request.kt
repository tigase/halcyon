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

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.Stanza
import tigase.halcyon.core.xmpp.stanzas.wrap

typealias ResultConverter<T> = (Element) -> T

abstract class Request<V, STT : Stanza<*>>(
	val jid: JID?, val id: String, val creationTimestamp: Long, val stanza: STT, var timeoutDelay: Long
) {

	protected val data = HashMap<String, Any>()

	/**
	 * `true` when no response for IQ or when stanza is not delivered to server (StreamManagement must be enabled)
	 */
	var isTimeout: Boolean = false
		protected set

	var isCompleted: Boolean = false
		private set

	var isSent: Boolean = false
		private set

	var response: STT? = null
		private set

	internal open fun markAsSent() {
		this.isSent = true
	}

	internal fun setResponseStanza(response: Element, processStack: Boolean = true) {
		this.response = wrap(response)
		isCompleted = true
		if (processStack) processStack()
		callHandlers()
	}

	internal fun markTimeout(processStack: Boolean = true) {
		isCompleted = true
		isTimeout = true
		if (processStack) processStack()
		callHandlers()
	}

	protected abstract fun processStack()

	protected abstract fun createRequestNotCompletedException(): RequestNotCompletedException

	protected abstract fun createRequestErrorException(
		error: ErrorCondition, text: String? = null
	): RequestErrorException

	protected abstract fun callHandlers()

	data class Error(val condition: ErrorCondition, val message: String?)

	protected fun findCondition(stanza: Element): Error {
		val error = stanza.children.firstOrNull { element -> element.name == "error" } ?: return Error(
			ErrorCondition.Unknown, null
		)
		// Condition Element
		val cnd =
			error.children.firstOrNull { element -> element.name != "text" && element.xmlns == XMPPException.XMLNS }
				?: return Error(ErrorCondition.Unknown, null)
		val txt =
			error.children.firstOrNull { element -> element.name == "text" && element.xmlns == XMPPException.XMLNS }

		val c = ErrorCondition.getByElementName(cnd.name)
		return Error(c, txt?.value)
	}

	fun isSet(param: String): Boolean {
		val v = data[param]
		return v != null && v is Boolean && v
	}

	fun setData(name: String, value: Any) {
		data[name] = value
	}

	fun getData(name: String): Any? {
		return data[name]
	}

	override fun toString(): String {
		return "Request[to=$jid, id=$id: ${stanza.getAsString()}]"
	}

}
