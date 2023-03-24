package tigase.halcyon.core.xml

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import tigase.halcyon.core.xmpp.stanzas.*

val HalcyonSerializerModule = SerializersModule {
	polymorphic(Element::class) {
		subclass(ElementImpl::class, ElementImplSerializer)
		default { ElementSerializer }
	}
}

object ElementSerializer : ElementSerializerImpl<Element>()
object ElementImplSerializer : ElementSerializerImpl<ElementImpl>()

object MessageStanzaSerialzer : StanzaSerializer<Message>()
object IQStanzaSerialzer : StanzaSerializer<IQ>()
object PresenceStanzaSerialzer : StanzaSerializer<Presence>()

open class StanzaSerializer<T : Stanza<*>> : ElementSerializerImpl<T>() {

	override fun deserialize(decoder: Decoder): T = wrap(super.deserialize(decoder))

}

open class ElementSerializerImpl<T : Element> : KSerializer<T> {

	override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
		"Element",
//		PrimitiveSerialDescriptor("name", PrimitiveKind.STRING),
//		PrimitiveSerialDescriptor("xmlns", PrimitiveKind.STRING),
//		PrimitiveSerialDescriptor("value", PrimitiveKind.STRING),
	) {
		element<String>("name")
		element<String>("value")
		element<Map<String, String>>("attrs")
		element<List<Element>>("children")
	}

	override fun serialize(encoder: Encoder, value: T) {
		encoder.encodeStructure(descriptor) {
			this.encodeStringElement(descriptor, 0, value.name)
			value.value?.let { this.encodeStringElement(descriptor, 1, it) }
			if (value.attributes.isNotEmpty()) this.encodeSerializableElement(
				descriptor, 2, MapSerializer(String.serializer(), String.serializer()), value.attributes
			)
			if (value.children.isNotEmpty()) this.encodeSerializableElement(
				descriptor, 3, ListSerializer(ElementSerializer), value.children as List<Element>
			)
		}
	}

	override fun deserialize(decoder: Decoder): T = decoder.decodeStructure(descriptor) {
		var name: String? = null
		var value: String? = null
		var attrs: Map<String, String>? = null
		var children: List<Element>? = null
		while (true) when (val index = decodeElementIndex(descriptor)) {
			0 -> name = decodeStringElement(descriptor, 0)
			1 -> value = decodeStringElement(descriptor, 1)
			2 -> attrs =
				decodeSerializableElement(descriptor, 2, MapSerializer(String.serializer(), String.serializer()))

			3 -> children = decodeSerializableElement(descriptor, 3, ListSerializer(ElementImplSerializer))
			CompositeDecoder.DECODE_DONE -> break
			else -> error("Unexpected index: $index")
		}
		ElementImpl(name!!).apply {
			this.value = value
			if (attrs != null) this.attributes.putAll(attrs)
			if (children != null) this.children.addAll(children)
		} as T
	}
}



