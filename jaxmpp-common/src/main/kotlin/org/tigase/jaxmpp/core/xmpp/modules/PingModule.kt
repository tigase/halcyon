package org.tigase.jaxmpp.core.xmpp.modules

import getTypeAttr
import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.modules.Criterion
import org.tigase.jaxmpp.core.modules.XmppModule
import org.tigase.jaxmpp.core.requests.Request
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xml.response
import org.tigase.jaxmpp.core.xml.stanza
import org.tigase.jaxmpp.core.xmpp.ErrorCondition
import org.tigase.jaxmpp.core.xmpp.JID
import org.tigase.jaxmpp.core.xmpp.StanzaType
import org.tigase.jaxmpp.core.xmpp.XMPPException

class PingModule : XmppModule {

	companion object {
		const val XMLNS = "urn:xmpp:ping"
		const val TYPE = XMLNS
	}

	override val type = TYPE
	override lateinit var context: Context
	override val criteria = Criterion.chain(Criterion.name("iq"), Criterion.xmlns(XMLNS))
	override val features = arrayOf(XMLNS)

	override fun initialize() {}

	fun ping(jid: JID? = null): Request {
		val stanza = stanza("iq") {
			id()
			if (jid != null) attribute("to", jid.toString())
			"ping"{
				xmlns = XMLNS
			}
		}
		return context.writer.write(stanza)
	}

	override fun process(element: Element) {
		when (element.getTypeAttr()) {
			StanzaType.Get -> {
				context.writer.write(response(element) { })
			}
			else -> throw XMPPException(ErrorCondition.NotAcceptable)
		}
	}

}