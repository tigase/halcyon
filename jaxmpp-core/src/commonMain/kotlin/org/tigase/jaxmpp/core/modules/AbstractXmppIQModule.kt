package org.tigase.jaxmpp.core.modules

import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xmpp.ErrorCondition
import org.tigase.jaxmpp.core.xmpp.XMPPException

abstract class AbstractXmppIQModule(
	type: String, features: Array<String>, criteria: Criteria
) : AbstractXmppModule(type, features, criteria) {

	final override fun process(element: Element) {
		val type = element.attributes["type"]
		when (type) {
			"set" -> processSet(element)
			"get" -> processGet(element)
			else -> throw XMPPException(ErrorCondition.BadRequest)
		}
	}

	abstract fun processGet(element: Element)

	abstract fun processSet(element: Element)

}