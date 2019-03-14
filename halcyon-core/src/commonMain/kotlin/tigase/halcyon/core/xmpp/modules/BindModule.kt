package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException

sealed class BindEvent(type: String) : tigase.halcyon.core.eventbus.Event(type) {

	class Success(val jid: JID) : BindEvent(TYPE) {
		companion object {
			const val TYPE = "tigase.halcyon.core.xmpp.modules.BindEvent.Success"
		}
	}

	class Error : BindEvent(TYPE) {
		companion object {
			const val TYPE = "tigase.halcyon.core.xmpp.modules.BindEvent.Error"
		}
	}

}

class BindModule : tigase.halcyon.core.modules.XmppModule {

	companion object {
		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-bind"
		const val TYPE = XMLNS
	}

	override val type = TYPE
	override lateinit var context: tigase.halcyon.core.Context
	override val criteria: tigase.halcyon.core.modules.Criteria? = null
	override val features = arrayOf(XMLNS)

	override fun initialize() {}

	fun bind() {
		val stanza = element("iq") {
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
		throw XMPPException(ErrorCondition.BadRequest)
	}

}