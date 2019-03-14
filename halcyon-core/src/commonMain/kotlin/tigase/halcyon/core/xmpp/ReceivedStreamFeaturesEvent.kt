package tigase.halcyon.core.xmpp

import tigase.halcyon.core.xml.Element

data class ReceivedStreamFeaturesEvent(val features: List<Element>) : tigase.halcyon.core.eventbus.Event(TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.xmpp.ReceivedStreamFeaturesEvent"
	}
}