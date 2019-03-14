package tigase.halcyon.core.modules

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException

abstract class AbstractXmppIQModule(
	type: String, features: Array<String>, criteria: tigase.halcyon.core.modules.Criteria
) : tigase.halcyon.core.modules.AbstractXmppModule(type, features, criteria) {

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