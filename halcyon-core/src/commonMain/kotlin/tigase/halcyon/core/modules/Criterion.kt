/*
 * halcyon-core
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core.modules

import tigase.halcyon.core.xml.Element

class Criterion private constructor() {

    companion object {

        fun element(predicate: (Element) -> Boolean): Criteria = object : Criteria {
            override fun match(element: Element): Boolean = predicate.invoke(element)
        }

        fun or(vararg crits: Criteria): Criteria = object : Criteria {
            override fun match(element: Element): Boolean =
                crits.any(predicate = { criteria -> criteria.match(element) })

            override fun toString(): String =
                "OR(" + crits.joinToString(", ") { it.toString() } + ")"
        }

        fun and(vararg crits: Criteria) = object : Criteria {
            override fun match(element: Element): Boolean =
                crits.all(predicate = { criteria -> criteria.match(element) })

            override fun toString(): String =
                "AND(" + crits.joinToString(", ") { it.toString() } + ")"
        }

        fun not(crit: Criteria) = object : Criteria {
            override fun match(element: Element): Boolean = !crit.match(element)
            override fun toString(): String = "!$crit"
        }

        fun name(name: String): Criteria = object : Criteria {
            override fun match(element: Element): Boolean = name == element.name
            override fun toString(): String = "{ name == $name }"
        }

        fun nameAndXmlns(name: String, xmlns: String): Criteria = object : Criteria {
            override fun match(element: Element): Boolean =
                name == element.name && xmlns == element.xmlns
            override fun toString(): String = "{ name == $name && xmlns == $xmlns }"
        }

        fun xmlns(xmlns: String): Criteria = object : Criteria {
            override fun match(element: Element): Boolean = xmlns == element.xmlns
            override fun toString(): String = "{ xmlns == $xmlns }"
        }

        fun chain(vararg children: Criteria): Criteria {
            fun find(children: List<Element>, cr: Criteria): Element? =
                children.firstOrNull { element -> cr.match(element) }

            return object : Criteria {
                override fun match(element: Element): Boolean {
                    var current: Element? = element
                    val it = children.iterator()
                    if (!it.hasNext() ||
                        !it.next()
                            .match(current!!)
                    ) {
                        return false
                    }

                    while (it.hasNext()) {
                        val cr = it.next()
                        current = find(current!!.children, cr)
                        if (current == null) return false
                    }

                    return true
                }

                override fun toString(): String = children.joinToString(".") { it.toString() }
            }
        }
    }
}
