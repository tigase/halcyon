package org.tigase.jaxmpp.core.xmpp

import org.tigase.jaxmpp.core.eventbus.Event
import org.tigase.jaxmpp.core.xml.Element

data class ReceivedStreamFeaturesEvent(val features: List<Element>) : Event(TYPE) {

	companion object {
		const val TYPE = "org.tigase.jaxmpp.core.xmpp.ReceivedStreamFeaturesEvent";
	}
}