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
package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element

class RSM private constructor() {

    companion object {

        const val XMLNS = "http://jabber.org/protocol/rsm"
        const val NAME = "set"

        fun parseResult(element: Element): Result {
            if (element.name != NAME) throw HalcyonException("Invalid RSM element name")
            if (element.xmlns != XMLNS) throw HalcyonException("Invalid RSM element XMLNS")
            var index: Int? = null
            var first: String? = null
            var last: String? = null
            var count: Int? = null

            element.getFirstChild("first")
                ?.let {
                    first = it.value
                    index = it.attributes["index"]?.toInt()
                }
            element.getFirstChild("last")
                ?.let { last = it.value }
            element.getFirstChild("count")
                ?.let { count = it.value?.toInt() }

            return Result(first, last, index, count)
        }

        fun query(init: QueryBuilder.() -> Unit): Query {
            val q = QueryBuilder()
            q.init()
            return q.build()
        }
    }

    class QueryBuilder internal constructor() {

        private var after: String? = null
        private var before: String? = null
        private var index: Int? = null
        private var max: Int? = null

        fun after(value: String = "") {
            this.after = value
        }

        fun before(value: String = "") {
            this.before = value
        }

        fun index(value: Int) {
            this.index = value
        }

        fun max(value: Int) {
            this.max = value
        }

        fun build(): Query = Query(after, before, index, max)
    }

    data class Result(
        val first: String? = null,
        val last: String? = null,
        val index: Int? = null,
        val count: Int? = null
    )

    data class Query(
        val after: String? = null,
        val before: String? = null,
        val index: Int? = null,
        val max: Int? = null
    ) {

        fun toElement(): Element = element("set") {
            xmlns = XMLNS

            after?.let { v ->
                "after" {
                    if (v.isNotBlank()) {
                        +v
                    }
                }
            }
            before?.let { v ->
                "before" {
                    if (v.isNotBlank()) {
                        +v
                    }
                }
            }
            max?.let {
                "max" {
                    +"$it"
                }
            }
            index?.let {
                "index" {
                    +"$it"
                }
            }
        }
    }
}
