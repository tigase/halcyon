package tigase.halcyon.core.modules

import tigase.halcyon.core.xml.Element

interface XmppModule {

	val type: String

	var context: tigase.halcyon.core.Context

	val criteria: tigase.halcyon.core.modules.Criteria?

	val features: Array<String>?

	fun initialize()

	fun process(element: Element)

}