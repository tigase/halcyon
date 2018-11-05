package org.tigase.jaxmpp.core.xmpp.modules

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.eventbus.Event
import org.tigase.jaxmpp.core.exceptions.JaXMPPException
import org.tigase.jaxmpp.core.modules.XmppModule
import org.tigase.jaxmpp.core.requests.Request
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xml.stanza
import org.tigase.jaxmpp.core.xmpp.JID

sealed class BindEvent(type: String) : Event(type) {

	class Success(val jid: JID) : BindEvent(TYPE) {
		companion object {
			const val TYPE = "org.tigase.jaxmpp.core.xmpp.modules.BindEvent.Success"
		}
	}

	class Error() : BindEvent(TYPE) {
		companion object {
			const val TYPE = "org.tigase.jaxmpp.core.xmpp.modules.BindEvent.Error"
		}
	}

}

class BindModule : XmppModule {

	companion object {
		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-bind"
		const val TYPE = XMLNS
	}

	override val type = TYPE
	override lateinit var context: Context
	override val criteria = null
	override val features = arrayOf(XMLNS)

	override fun initialize() {}

	fun bind() {
		val stanza = stanza("iq") {
			id()
			attribute("type", "set")
			"bind"{
				xmlns = XMLNS
			}
		}
		context.writer.write(stanza).response { request, element, result ->
			when (result) {
				is Request.Result.Success -> {
					val bind = result.responseStanza.getChildrenNS("bind", XMLNS)!!
					val jidElement = bind.getFirstChild("jid")!!
					val jid = JID.parse(jidElement.value!!)
					context.sessionObject.setProperty(XMLNS, jid)
					context.eventBus.fire(BindEvent.Success(jid))
				}
			}
		}
	}

	override fun process(element: Element) {
		throw JaXMPPException("Not supported")
	}

}