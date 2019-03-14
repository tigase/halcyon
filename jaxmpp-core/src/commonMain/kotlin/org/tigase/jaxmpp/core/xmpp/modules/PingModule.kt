package org.tigase.jaxmpp.core.xmpp.modules

import org.tigase.jaxmpp.core.modules.AbstractXmppIQModule
import org.tigase.jaxmpp.core.modules.Criterion
import org.tigase.jaxmpp.core.requests.Request
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xml.element
import org.tigase.jaxmpp.core.xml.response
import org.tigase.jaxmpp.core.xmpp.ErrorCondition
import org.tigase.jaxmpp.core.xmpp.JID
import org.tigase.jaxmpp.core.xmpp.XMPPException

class PingModule :
	AbstractXmppIQModule(TYPE, arrayOf(XMLNS), Criterion.chain(Criterion.name("iq"), Criterion.xmlns(XMLNS))) {

	companion object {
		const val XMLNS = "urn:xmpp:ping"
		const val TYPE = XMLNS
	}

	fun ping(jid: JID? = null): Request {
		val stanza = element("iq") {
			id()
			if (jid != null) attribute("to", jid.toString())
			"ping"{
				xmlns = XMLNS
			}
		}
		return context.writer.write(stanza)
	}

	override fun processGet(element: Element) {
		context.writer.write(response(element) { })
	}

	override fun processSet(element: Element) {
		throw XMPPException(ErrorCondition.NotAcceptable)
	}

}