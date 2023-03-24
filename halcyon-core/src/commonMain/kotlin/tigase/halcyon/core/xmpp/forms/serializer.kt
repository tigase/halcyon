package tigase.halcyon.core.xmpp.forms

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tigase.halcyon.core.xml.ElementSerializer

object JabberDataFormSerializer : KSerializer<JabberDataForm> {

	override val descriptor: SerialDescriptor = ElementSerializer.descriptor

	override fun deserialize(decoder: Decoder): JabberDataForm {
		val e = decoder.decodeSerializableValue(ElementSerializer)
		return JabberDataForm(e)
	}

	override fun serialize(encoder: Encoder, value: JabberDataForm) {
		encoder.encodeSerializableValue(ElementSerializer, value.element)
	}
}