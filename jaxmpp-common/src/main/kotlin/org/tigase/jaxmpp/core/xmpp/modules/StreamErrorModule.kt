package org.tigase.jaxmpp.core.xmpp.modules

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.eventbus.Event
import org.tigase.jaxmpp.core.modules.Criterion
import org.tigase.jaxmpp.core.modules.XmppModule
import org.tigase.jaxmpp.core.xml.Element

data class StreamErrorEvent(val error: Element) : Event(TYPE) {

	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.xmpp.modules.StreamErrorEvent";
	}
}

class StreamErrorModule : XmppModule {

	companion object {
		const val TYPE = "StreamErrorModule"
	}

	override val type = TYPE
	override lateinit var context: Context
	override val criteria = Criterion.and(Criterion.name("error"), Criterion.xmlns("http://etherx.jabber.org/streams"))
	override val features: Array<String>? = null

	override fun initialize() {}

	override fun process(element: Element) {
		context.eventBus.fire(StreamErrorEvent(element.getFirstChild()!!))
	}
}