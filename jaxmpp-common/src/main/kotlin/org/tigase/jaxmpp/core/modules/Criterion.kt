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

		fun chain(vararg children: Criteria): Criteria {

			fun find(children: List<Element>, cr: Criteria): Element? {
				return children.firstOrNull { element -> cr.match(element) }
			}

			return object : Criteria {
				override fun match(element: Element): Boolean {
					var current: Element? = element
					val it = children.iterator()
					if (!it.hasNext() || !it.next().match(current!!)) return false

					while (it.hasNext()) {
						val cr = it.next()
						current = find(current!!.children, cr)
						if (current == null) return false
					}

					return true
				}
			}
		}

	}
}