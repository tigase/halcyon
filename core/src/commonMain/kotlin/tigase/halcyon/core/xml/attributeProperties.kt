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

import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.toJID
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class AttributeProperty<V>(private val attributeName: String? = null) : ReadWriteProperty<Element, V> {

	override fun getValue(thisRef: Element, property: KProperty<*>): V {
		val str = thisRef.attributes[attributeName ?: property.name]
		return stringToValue(str)
	}

	override fun setValue(thisRef: Element, property: KProperty<*>, value: V) {
		val str = valueToString(value)
		if (str == null) thisRef.attributes.remove(attributeName ?: property.name)
		else thisRef.attributes[attributeName ?: property.name] = str
	}

	abstract fun valueToString(value: V): String?
	abstract fun stringToValue(value: String?): V
}

inline fun <V> attributeProp(
	attributeName: String? = null, crossinline valueToString: (V) -> String?, crossinline stringToValue: (String?) -> V,
): ReadWriteProperty<Element, V> {
	return object : AttributeProperty<V>(attributeName) {
		override fun valueToString(value: V): String? = valueToString.invoke(value)

		override fun stringToValue(value: String?): V = stringToValue.invoke(value)

	}
}

fun intAttributeProperty(attributeName: String? = null): ReadWriteProperty<Element, Int?> =
	attributeProp(attributeName = attributeName,
				  valueToString = { v -> v?.toString() },
				  stringToValue = { s -> s?.toInt() })

fun stringAttributeProperty(attributeName: String? = null): ReadWriteProperty<Element, String?> =
	attributeProp(attributeName = attributeName, valueToString = { v -> v }, stringToValue = { s -> s })

fun jidAttributeProperty(attributeName: String? = null): ReadWriteProperty<Element, JID?> =
	attributeProp(attributeName = attributeName,
				  valueToString = { v -> v?.toString() },
				  stringToValue = { v: String? -> v?.toJID() })
