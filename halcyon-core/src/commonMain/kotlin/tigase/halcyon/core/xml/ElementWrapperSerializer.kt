package tigase.halcyon.core.xml

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.Presence
import tigase.halcyon.core.xmpp.stanzas.Stanza
import tigase.halcyon.core.xmpp.stanzas.wrap

object ElementImplSerializer :
    ElementWrapperSerializer<ElementImpl>(
        elementProvider = { it },
        objectFactory = { it as ElementImpl }
    )

object StanzaSerializer :
    ElementWrapperSerializer<Stanza<*>>(
        elementProvider = { it },
        objectFactory = { wrap(it) }
    )

object MessageStanzaSerializer :
    ElementWrapperSerializer<Message>(
        elementProvider = { it },
        objectFactory = { Message(it) }
    )

object PresenceStanzaSerializer :
    ElementWrapperSerializer<Presence>(
        elementProvider = { it },
        objectFactory = { Presence(it) }
    )

object IQStanzaSerializer :
    ElementWrapperSerializer<IQ>(
        elementProvider = { it },
        objectFactory = { IQ(it) }
    )

open class ElementWrapperSerializer<T>(
    private val elementProvider: (T) -> Element,
    private val objectFactory: (Element) -> T
) : KSerializer<T> {

    private val elementSerializer: KSerializer<Element> = Element.serializer()

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        SerialDescriptor("wrapper", elementSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: T) {
        val element = elementProvider(value)
        encoder.encodeSerializableValue(elementSerializer, element)
    }

    override fun deserialize(decoder: Decoder): T {
        val element = decoder.decodeSerializableValue(elementSerializer)
        return objectFactory(element)
    }
}
