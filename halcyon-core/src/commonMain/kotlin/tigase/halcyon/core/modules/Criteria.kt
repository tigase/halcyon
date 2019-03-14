package tigase.halcyon.core.modules

import tigase.halcyon.core.xml.Element

interface Criteria {

	fun match(element: Element): Boolean

}