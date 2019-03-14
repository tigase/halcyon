package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.xml.Element

data class StreamErrorEvent(val error: Element) : tigase.halcyon.core.eventbus.Event(TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.xmpp.modules.StreamErrorEvent"
	}
}

class StreamErrorModule : tigase.halcyon.core.modules.XmppModule {

	companion object {
		const val TYPE = "StreamErrorModule"
	}

	override val type = TYPE
	override lateinit var context: tigase.halcyon.core.Context
	override val criteria = tigase.halcyon.core.modules.Criterion.and(
		tigase.halcyon.core.modules.Criterion.name("error"),
		tigase.halcyon.core.modules.Criterion.xmlns("http://etherx.jabber.org/streams")
	)
	override val features: Array<String>? = null

	override fun initialize() {}

	override fun process(element: Element) {
		context.eventBus.fire(StreamErrorEvent(element.getFirstChild()!!))
	}
}