package org.tigase.jaxmpp.core.xmpp.modules

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.SessionObject
import org.tigase.jaxmpp.core.eventbus.Event
import org.tigase.jaxmpp.core.modules.Criterion
import org.tigase.jaxmpp.core.modules.XmppModule
import org.tigase.jaxmpp.core.xml.Element

data class StreamFeaturesEvent(val features: Element) : Event(TYPE) {

	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.xmpp.modules.StreamFeaturesEvent";
	}
}

class StreamFeaturesModule : XmppModule {

	companion object {
		const val TYPE = "StreamFeaturesModule"
		const val FEATURES_KEY = "StreamFeaturesModule.Features"

		fun getFeatures(sessionObject: SessionObject): Element? = sessionObject.getProperty<Element>(FEATURES_KEY)

		fun isFeatureAvailable(sessionObject: SessionObject, name: String, xmlns: String): Boolean = getFeatures(
				sessionObject)?.getChildrenNS(name, xmlns) != null
	}

	override val type = TYPE
	override lateinit var context: Context
	override val criteria = Criterion.and(Criterion.name("features"),
										  Criterion.xmlns("http://etherx.jabber.org/streams"))
	override val features: Array<String>? = null

	override fun initialize() {}

	override fun process(element: Element) {
		context.sessionObject.setProperty(SessionObject.Scope.stream, FEATURES_KEY, element)
		context.eventBus.fire(StreamFeaturesEvent(element))
	}
}