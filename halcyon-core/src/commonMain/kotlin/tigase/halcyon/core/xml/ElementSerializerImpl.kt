package tigase.halcyon.core.xml

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*

object ElementListSerializer : KSerializer<List<Element>> {

    val listSerializer: KSerializer<List<Element>> = ListSerializer(Element.serializer())

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        SerialDescriptor("children", listSerializer.descriptor)

    override fun deserialize(decoder: Decoder): List<Element> =
        decoder.decodeSerializableValue(listSerializer)

    override fun serialize(encoder: Encoder, value: List<Element>) {
        encoder.encodeSerializableValue(listSerializer, value)
    }
}

object ElementSerializer : KSerializer<Element> {
    override val descriptor = buildClassSerialDescriptor("element") {
        element<String>("name")
        element<String>("value")
        element<Map<String, String>>("attrs")
        element<List<String>>("children")
    }

    override fun deserialize(decoder: Decoder): Element {
        var name: String? = null
        var value: String? = null
        var attrs: Map<String, String>? = null
        var children: List<Element>? = null

        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> name = decodeStringElement(descriptor, 0)
                    1 -> value = decodeStringElement(descriptor, 1)
                    2 -> attrs = decodeSerializableElement(
                        descriptor,
                        2,
                        MapSerializer(String.serializer(), String.serializer())
                    )
                    3 ->
                        children =
                            decodeSerializableElement(
                                descriptor,
                                3,
                                ElementListSerializer,
                                children
                            )

                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        requireNotNull(name)

        return ElementImpl(name!!).apply {
            this.value = value
            attrs?.let {
                this.attributes.putAll(it)
            }
            children?.let {
                it.forEach { this.add(it) }
            }
        }
    }

    override fun serialize(encoder: Encoder, value: Element) {
        encoder.encodeStructure(descriptor) {
            this.encodeStringElement(descriptor, 0, value.name)
            value.value?.let { this.encodeStringElement(descriptor, 1, it) }
            if (value.attributes.isNotEmpty()) {
                this.encodeSerializableElement(
                    descriptor,
                    2,
                    MapSerializer(String.serializer(), String.serializer()),
                    value.attributes
                )
            }
            if (value.children.isNotEmpty()) {
                this.encodeSerializableElement(
                    descriptor,
                    3,
                    ElementListSerializer,
                    value.children
                )
            }
        }
    }
}
