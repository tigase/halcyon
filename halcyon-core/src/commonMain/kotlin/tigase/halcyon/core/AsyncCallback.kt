package tigase.halcyon.core

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition

interface AsyncCallback {

	fun oSuccess(responseStanza: Element)

	fun onError(responseStanza: Element, condition: ErrorCondition)

	fun onTimeout()

}