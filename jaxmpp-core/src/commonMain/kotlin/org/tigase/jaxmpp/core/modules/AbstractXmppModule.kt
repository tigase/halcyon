package org.tigase.jaxmpp.core.modules

import org.tigase.jaxmpp.core.Context
import org.tigase.jaxmpp.core.modules.Criteria
import org.tigase.jaxmpp.core.modules.XmppModule

abstract class AbstractXmppModule(
	final override val type: String,
	final override val features: Array<String>,
	final override val criteria: Criteria
) : XmppModule {

	final override lateinit var context: Context

	override fun initialize() {
	}

}