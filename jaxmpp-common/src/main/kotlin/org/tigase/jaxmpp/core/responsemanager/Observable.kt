package org.tigase.jaxmpp.core.responsemanager

import org.tigase.jaxmpp.core.AsyncCallback
import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xmpp.ErrorCondition

interface Observable {
	fun listen(init: ResponseManager.HandlerHelper.() -> Unit)
	fun listen(callback: AsyncCallback)
	fun listen(onSuccess: (Element) -> Unit, onError: (Element, ErrorCondition) -> Unit, onTimeout: () -> Unit)
}