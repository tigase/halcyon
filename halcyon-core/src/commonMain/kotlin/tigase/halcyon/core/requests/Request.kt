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

import getTypeAttr
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.StanzaType
import tigase.halcyon.core.xmpp.XMPPException

class Request(val jid: JID?, val id: String, val creationTimestamp: Long, val requestStanza: Element) {

	interface Callback {
		fun success(request: Request, responseStanza: Element)
		fun error(request: Request, responseStanza: Element, errorCondition: ErrorCondition)
		fun timeout(request: Request)
	}

	sealed class Result {
		class Success(val responseStanza: Element) : Result()

		class Error(val responseStanza: Element, val errorCondition: ErrorCondition) : Result()

		class Timeout : Result()
	}

	class HandlerHelper : Callback {

		override fun success(request: Request, responseStanza: Element) {
			successHandler?.invoke(request, responseStanza)
		}

		override fun error(request: Request, responseStanza: Element, errorCondition: ErrorCondition) {
			errorHandler?.invoke(request, responseStanza, errorCondition)
		}

		override fun timeout(request: Request) {
			timeoutHandler?.invoke(request)
		}

		private var successHandler: ((Request, Element) -> Unit)? = null
		private var errorHandler: ((Request, Element, ErrorCondition) -> Unit)? = null
		private var timeoutHandler: ((Request) -> Unit)? = null

		fun success(handler: (Request, Element) -> Unit) {
			this.successHandler = handler
		}

		fun error(handler: (Request, Element, ErrorCondition) -> Unit) {
			this.errorHandler = handler
		}

		fun timeout(handler: (Request) -> Unit) {
			this.timeoutHandler = handler
		}

	}

	private val data = HashMap<String, Any>()

	var responseStanza: Element? = null
		internal set(value) {
			field = value
			callHandlers()
		}

	private var callback: Callback? = null

	/**
	 * Delay to timeout this request in milliseconds.
	 */
	var timeoutDelay = 30000

	private fun findCondition(stanza: Element): ErrorCondition {
		val error = stanza.children.firstOrNull { element -> element.name == "error" } ?: return ErrorCondition.Unknown
		val cnd = error.children.firstOrNull { element -> element.xmlns == XMPPException.XMLNS } ?: return ErrorCondition.Unknown
		return ErrorCondition.getByElementName(cnd.name)
	}

	private fun callHandlers() {
		if (responseStanza == null || callback == null) return

		val type = responseStanza!!.getTypeAttr()
		if (type == StanzaType.Result) {
			callback!!.success(this, responseStanza!!)
		} else if (type == StanzaType.Error) {
			callback!!.error(this, responseStanza!!, findCondition(responseStanza!!))
		}
	}

	private class LambdaToCallback(val callback: (Request, Element?, Result) -> Unit) : Callback {
		override fun success(request: Request, responseStanza: Element) {
			callback.invoke(request, responseStanza, Result.Success(responseStanza))
		}

		override fun error(request: Request, responseStanza: Element, errorCondition: ErrorCondition) {
			callback.invoke(request, responseStanza, Result.Error(responseStanza, errorCondition))
		}

		override fun timeout(request: Request) {
			callback.invoke(request, null, Result.Timeout())
		}
	}

	fun response(callback: (Request, Element?, Result) -> Unit) {
		response(LambdaToCallback(callback))
	}

	fun response(callback: Callback) {
		this.callback = callback
		callHandlers()
	}

	fun handle(init: HandlerHelper.() -> Unit) {
		val callback = HandlerHelper()
		callback.init()
		response(callback)
	}

	internal fun callTimeout() {
		val stanzaType = requestStanza.getTypeAttr()
		if (stanzaType == StanzaType.Get || stanzaType == StanzaType.Set) {
			callback?.timeout(this)
		}
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
		return "Request[to=$jid, id=$id: ${requestStanza.getAsString()}]"
	}

}