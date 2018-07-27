package org.tigase.jaxmpp.core.modules

import org.tigase.jaxmpp.core.xml.Element

class Criterion private constructor() {

	companion object {
		fun or(vararg crits: Criteria): Criteria = object : Criteria {
			override fun match(element: Element): Boolean = crits.firstOrNull(
					predicate = { criteria -> criteria.match(element) }) != null
		}

		fun and(vararg crits: Criteria) = object : Criteria {
			override fun match(element: Element): Boolean = crits.filter(
					predicate = { criteria -> criteria.match(element) }).size == crits.size
		}

		fun not(crit: Criteria) = object : Criteria {
			override fun match(element: Element): Boolean = !crit.match(element)
		}

		fun name(name: String): Criteria {
			return object : Criteria {
				override fun match(element: Element): Boolean = name == element.name
			}
		}

		fun nameAndXmlns(name: String, xmlns: String): Criteria {
			return object : Criteria {
				override fun match(element: Element): Boolean = name == element.name && xmlns == element.xmlns
			}
		}

		fun xmlns(xmlns: String): Criteria {
			return object : Criteria {
				override fun match(element: Element): Boolean = xmlns == element.xmlns
			}
		}

	}
}