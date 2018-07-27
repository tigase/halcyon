package org.tigase.jaxmpp.core

import org.tigase.jaxmpp.core.requests.Request
import org.tigase.jaxmpp.core.xml.Element

interface PacketWriter {

	fun write(stanza: Element): Request

	fun writeDirectly(stanza: Element)

}