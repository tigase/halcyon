package tigase.halcyon.core.xmpp

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object FullJIDSerializer : KSerializer<FullJID> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FullJID) {
        val string = value.toString()
        encoder.encodeString(string)
    }

    override fun deserialize(decoder: Decoder): FullJID = decoder.decodeString().toFullJID()
}

object BareJIDSerializer : KSerializer<BareJID> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BareJID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BareJID) {
        val string = value.toString()
        encoder.encodeString(string)
    }

    override fun deserialize(decoder: Decoder): BareJID = decoder.decodeString().toBareJID()
}
