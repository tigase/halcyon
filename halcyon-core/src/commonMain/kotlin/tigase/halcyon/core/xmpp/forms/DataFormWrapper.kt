package tigase.halcyon.core.xmpp.forms

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.toBareJID
import tigase.halcyon.core.xmpp.toJID
import kotlin.reflect.KClass

data class FieldMetadata(val name: String, val label: String?, val type: FieldType, val required: Boolean)

abstract class DataFormWrapper {

	companion object {

		const val XMLNS = "jabber:x:data"
	}

	private val _fields = mutableMapOf<String, FieldMetadata>()
	val fields: Map<String, FieldMetadata> = _fields

	fun setFields(fields: Collection<FieldMetadata>){
		_fields.clear()
		_fields.putAll(fields.associateBy { it.name })
	}

}

private fun createFieldValue(value: Any?): Element = element("value") {
	when (value) {
		is XmppValuedEnum -> +value.xmppValue
		else -> +"$value"
	}
}

fun createFieldElement(value: Any?, fieldName: String, metadata: FieldMetadata?): Element {
	return element("field") {
		attributes["var"] = fieldName
		attributes["type"] = metadata!!.type.xmppValue
		when (value) {
			is Array<*> -> {
				value.forEach { addChild(createFieldValue(it)) }
			}
			is Collection<*> -> {
				value.forEach { addChild(createFieldValue(it)) }
			}
			else -> value?.let {
				addChild(createFieldValue(it))
			}
		}
	}
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> convertToSimpleObject(values: List<String>, kClass: KClass<T>): T {
	if (values.size > 1) throw RuntimeException("Multi values are not supported!")
	return when (kClass) {
		Int::class -> values[0].toInt() as T
		Double::class -> values[0].toDouble() as T
		String::class -> values[0] as T
		BareJID::class -> values[0].toBareJID() as T
		JID::class -> values[0].toJID() as T
		else -> throw RuntimeException("Type $kClass is not supported yet")
	}
}

fun <T : Enum<*>> convertToEnum(values: List<String>, allEnums: Array<T>): T {
	if (values.size > 1) throw RuntimeException("Multi values are not supported!")
	val x = allEnums.associateBy {
		if (it is XmppValuedEnum) {
			it.xmppValue
		} else it.name
	}
	return x[values[0]] as T
}

fun <T : Any> convertToListOfSimpleObjects(values: List<String>, kClass: KClass<T>): List<T> {
	return values.map { convertToSimpleObject(listOf(it), kClass) }
}

fun <T : Enum<*>> convertToListOfEnum(values: List<String>, allEnums: Array<T>): List<T> {
	return values.map { convertToEnum(listOf(it), allEnums) }
}
