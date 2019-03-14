package tigase.halcyon.core

import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.xml.Element

interface PacketWriter {

	fun write(stanza: Element): Request

	fun writeDirectly(stanza: Element)

}