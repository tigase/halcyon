package org.tigase.jaxmpp.core.modules

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.xml.Element

interface XmppModule {

	val type: String

	var context: Context

	val criteria: Criteria?

	val features: Array<String>?

	fun initialize()

	fun process(element: Element)

}