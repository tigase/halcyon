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

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class ElementProperty<V>(private val elementName: String? = null) :
    ReadWriteProperty<Element, V> {

    override fun getValue(thisRef: Element, property: KProperty<*>): V {
        val str = thisRef.getFirstChild(elementName ?: property.name)?.value
        return stringToValue(str)
    }

    override fun setValue(thisRef: Element, property: KProperty<*>, value: V) {
        val strValue = valueToString(value)
        val childName = elementName ?: property.name
        var c = thisRef.getFirstChild(childName)
        if (value == null && c != null) {
            thisRef.remove(c)
        } else if (value != null && c != null) {
            c.value = strValue
        } else if (value != null && c == null) {
            c = ElementImpl(childName)
            c.value = strValue
            thisRef.add(c)
        }
    }

    abstract fun valueToString(value: V): String?
    abstract fun stringToValue(value: String?): V
}

inline fun <V> elementProperty(
    elementName: String? = null,
    crossinline valueToString: (V) -> String?,
    crossinline stringToValue: (String?) -> V
): ReadWriteProperty<Element, V> = object : ElementProperty<V>(elementName) {
    override fun valueToString(value: V): String? = valueToString.invoke(value)

    override fun stringToValue(value: String?): V = stringToValue.invoke(value)
}

fun intElementProperty(elementName: String? = null): ReadWriteProperty<Element, Int?> =
    elementProperty(
        elementName = elementName,
        valueToString = { v -> v?.toString() },
        stringToValue = { s -> s?.toInt() }
    )

fun intWithDefaultElementProperty(
    elementName: String? = null,
    defaultValue: Int
): ReadWriteProperty<Element, Int> = elementProperty(
    elementName = elementName,
    valueToString = { v -> v.toString() },
    stringToValue = { s -> s?.toInt() ?: defaultValue }
)

fun stringElementProperty(elementName: String? = null): ReadWriteProperty<Element, String?> =
    elementProperty(elementName = elementName, valueToString = { v ->
        v
    }, stringToValue = { s -> s })
