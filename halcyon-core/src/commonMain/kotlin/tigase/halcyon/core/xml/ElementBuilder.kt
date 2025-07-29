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
package tigase.halcyon.core.xml

import kotlin.jvm.JvmStatic

class ElementBuilder private constructor(private val element: Element) {

    var currentElement: Element = element
        private set

    val onTop: Boolean
        get() = currentElement.parent == null

    fun child(name: String): ElementBuilder {
        val element = ElementImpl(name)
        element.parent = currentElement
        currentElement.children.add(element)
        currentElement = element
        return this
    }

    fun build(): Element = element

    fun attribute(key: String, value: String): ElementBuilder {
        currentElement.attributes[key] = value
        return this
    }

    fun attributes(attributes: Map<String, String>): ElementBuilder {
        currentElement.attributes.putAll(attributes)
        return this
    }

    fun value(value: String): ElementBuilder {
        currentElement.value = value
        return this
    }

    fun xmlns(xmlns: String): ElementBuilder {
        currentElement.attributes["xmlns"] = xmlns
        return this
    }

    fun up(): ElementBuilder {
        currentElement = currentElement.parent!!
        return this
    }

    companion object {

        @JvmStatic
        fun create(name: String): ElementBuilder = ElementBuilder(ElementImpl(name))

        @JvmStatic
        fun create(name: String, xmlns: String): ElementBuilder {
            val element = ElementImpl(name)
            element.attributes["xmlns"] = xmlns
            return ElementBuilder(element)
        }
    }
}
