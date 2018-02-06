package org.tigase.jaxmpp.core

import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xmpp.ErrorCondition

interface AsyncCallback {

	fun oSuccess(responseStanza: Element)

	fun onError(responseStanza: Element, condition: ErrorCondition)

	fun onTimeout()

}