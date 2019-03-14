package tigase.halcyon.core.modules

import tigase.halcyon.core.xml.Element

class Criterion private constructor() {

	companion object {
		fun or(vararg crits: tigase.halcyon.core.modules.Criteria): tigase.halcyon.core.modules.Criteria =
			object : tigase.halcyon.core.modules.Criteria {
			override fun match(element: Element): Boolean = crits.firstOrNull(
					predicate = { criteria -> criteria.match(element) }) != null
		}

		fun and(vararg crits: tigase.halcyon.core.modules.Criteria) = object : tigase.halcyon.core.modules.Criteria {
			override fun match(element: Element): Boolean = crits.filter(
					predicate = { criteria -> criteria.match(element) }).size == crits.size
		}

		fun not(crit: tigase.halcyon.core.modules.Criteria) = object : tigase.halcyon.core.modules.Criteria {
			override fun match(element: Element): Boolean = !crit.match(element)
		}

		fun name(name: String): tigase.halcyon.core.modules.Criteria {
			return object : tigase.halcyon.core.modules.Criteria {
				override fun match(element: Element): Boolean = name == element.name
			}
		}

		fun nameAndXmlns(name: String, xmlns: String): tigase.halcyon.core.modules.Criteria {
			return object : tigase.halcyon.core.modules.Criteria {
				override fun match(element: Element): Boolean = name == element.name && xmlns == element.xmlns
			}
		}

		fun xmlns(xmlns: String): tigase.halcyon.core.modules.Criteria {
			return object : tigase.halcyon.core.modules.Criteria {
				override fun match(element: Element): Boolean = xmlns == element.xmlns
			}
		}

		fun chain(vararg children: tigase.halcyon.core.modules.Criteria): tigase.halcyon.core.modules.Criteria {

			fun find(children: List<Element>, cr: tigase.halcyon.core.modules.Criteria): Element? {
				return children.firstOrNull { element -> cr.match(element) }
			}

			return object : tigase.halcyon.core.modules.Criteria {
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