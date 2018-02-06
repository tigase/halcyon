package org.tigase.jaxmpp.core

import org.tigase.jaxmpp.core.xml.Element

interface PacketWriter {

	fun write(stanza: Element)

	fun write(stanza: Element, asyncCallback: AsyncCallback)

	fun write(stanza: Element, timeout: Long, asyncCallback: AsyncCallback)

}