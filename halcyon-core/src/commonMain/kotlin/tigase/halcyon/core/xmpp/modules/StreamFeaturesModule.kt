package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.SessionObject
import tigase.halcyon.core.xml.Element

data class StreamFeaturesEvent(val features: Element) : tigase.halcyon.core.eventbus.Event(TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.xmpp.modules.StreamFeaturesEvent"
	}
}

class StreamFeaturesModule : tigase.halcyon.core.modules.XmppModule {

	companion object {
		const val TYPE = "StreamFeaturesModule"
		const val FEATURES_KEY = "StreamFeaturesModule.Features"

		fun getFeatures(sessionObject: tigase.halcyon.core.SessionObject): Element? =
			sessionObject.getProperty<Element>(FEATURES_KEY)

		fun isFeatureAvailable(sessionObject: tigase.halcyon.core.SessionObject, name: String, xmlns: String): Boolean =
			getFeatures(
				sessionObject
			)?.getChildrenNS(name, xmlns) != null
	}

	override val type = TYPE
	override lateinit var context: tigase.halcyon.core.Context
	override val criteria = tigase.halcyon.core.modules.Criterion.and(
		tigase.halcyon.core.modules.Criterion.name("features"),
		tigase.halcyon.core.modules.Criterion.xmlns("http://etherx.jabber.org/streams")
	)
	override val features: Array<String>? = null

	override fun initialize() {}

	override fun process(element: Element) {
		context.sessionObject.setProperty(tigase.halcyon.core.SessionObject.Scope.stream, FEATURES_KEY, element)
		context.eventBus.fire(StreamFeaturesEvent(element))
	}
}