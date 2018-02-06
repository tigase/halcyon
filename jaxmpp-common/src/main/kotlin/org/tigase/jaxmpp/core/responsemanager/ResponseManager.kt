package org.tigase.jaxmpp.core.responsemanager

import getFromAttr
import getIdAttr
import getToAttr
import getTypeAttr
import org.tigase.jaxmpp.core.AsyncCallback
import org.tigase.jaxmpp.core.exceptions.JaXMPPException
import org.tigase.jaxmpp.core.excutor.Executor
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xmpp.ErrorCondition
import org.tigase.jaxmpp.core.xmpp.JID
import org.tigase.jaxmpp.core.xmpp.StanzaType
import org.tigase.jaxmpp.core.xmpp.XMPPException

class ResponseManager {

	private val executor = Executor()

	class HandlerHelper : AsyncCallback {
		override fun oSuccess(responseStanza: Element) {
			try {
				successHandler.invoke(responseStanza)
			} catch (e: kotlin.UninitializedPropertyAccessException) {
			}
		}

		override fun onError(response: Element, error: ErrorCondition) {
			try {
				errorHandler.invoke(response, error)
			} catch (e: kotlin.UninitializedPropertyAccessException) {
			}
		}

		override fun onTimeout() {
			try {
				timeoutHandler.invoke()
			} catch (e: kotlin.UninitializedPropertyAccessException) {
			}
		}

		private lateinit var successHandler: (Element) -> Unit
		private lateinit var errorHandler: (Element, ErrorCondition) -> Unit
		private lateinit var timeoutHandler: () -> Unit

		fun onSuccess(handler: (Element) -> Unit) {
			this.successHandler = handler
		}

		fun onError(handler: (Element, ErrorCondition) -> Unit) {
			this.errorHandler = handler
		}

		fun onTimeout(handler: () -> Unit) {
			this.timeoutHandler = handler

		}

	}

	class ResponseHandler(val jid: JID?, val id: String, val element: Element) : Observable {

		var callback: AsyncCallback? = null
			private set

		override fun listen(init: HandlerHelper.() -> Unit) {
			val listener = HandlerHelper()
			listener.init()
			listen(listener)
		}

		override fun listen(callback: AsyncCallback) {
			this.callback = callback;
		}

		override fun listen(onSuccess: (Element) -> Unit, onError: (Element, ErrorCondition) -> Unit,
							onTimeout: () -> Unit) {
			listen(object : AsyncCallback {
				override fun onError(response: Element, error: ErrorCondition) {
					onError.invoke(response, error); }

				override fun onTimeout() {
					onTimeout.invoke()
				}

				override fun oSuccess(response: Element) {
					onSuccess.invoke(response)
				}

			})
		}
	}

	private val handlers = HashMap<String, ResponseHandler>()

	fun registerRequest(element: Element): Observable {
		val id = element.getIdAttr() ?: throw JaXMPPException("Stanza must contains 'id' attribute")
		val jid = element.getToAttr()

		val h = ResponseHandler(jid, id, element)
		synchronized(this) {
			handlers[id] = h
		}
		return h
	}

	fun registerRequest(element: Element, callback: AsyncCallback) {
		val res = registerRequest(element)
		res.listen(callback)
	}

	fun getHandler(response: Element): ResponseHandler? {
		val id = response.attributes["id"] ?: return null
		val from = response.attributes["from"]

		val handler = synchronized(this) { handlers[id] } ?: return null

		if (!verify(handler, response)) return null

		synchronized(this) { handlers.remove(id) }
		return handler
	}

	private fun verify(entry: ResponseHandler, response: Element): Boolean {
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

	fun run(handler: ResponseHandler, stanza: Element) {
		val callback = handler.callback ?: return
		val type = stanza.getTypeAttr();

		executor.execute {
			if (type == StanzaType.Result) {
				callback.oSuccess(stanza)
			} else if (type == StanzaType.Error) {
				callback.onError(stanza, findCondition(stanza))
			}
		}
	}

	fun getAndRun(response: Element): Boolean {
		var r = getHandler(response) ?: return false;
		run(r, response)
		return true
	}

	private fun findCondition(stanza: Element): ErrorCondition {
		val error = stanza.children.firstOrNull { element -> element.name == "error" } ?: return ErrorCondition.Unknown
		val cnd = error.children.firstOrNull { element -> element.xmlns == XMPPException.XMLNS }
				?: return ErrorCondition.Unknown
		return ErrorCondition.getByElementName(cnd.name)
	}

}