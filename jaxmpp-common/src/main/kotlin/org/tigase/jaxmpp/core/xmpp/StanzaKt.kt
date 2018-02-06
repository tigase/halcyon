import org.tigase.jaxmpp.core.xmpp.JID

fun org.tigase.jaxmpp.core.xml.Element.getFromAttr(): JID? {
	val tmp = this.attributes["from"] ?: return null
	return JID.parse(tmp)
}

fun org.tigase.jaxmpp.core.xml.Element.getToAttr(): JID? {
	val tmp = this.attributes["to"] ?: return null
	return JID.parse(tmp)
}

fun org.tigase.jaxmpp.core.xml.Element.getIdAttr(): String? {
	return this.attributes["id"]
}

fun org.tigase.jaxmpp.core.xml.Element.getTypeAttr(): org.tigase.jaxmpp.core.xmpp.StanzaType {
	val tmp = this.attributes["type"] ?: return org.tigase.jaxmpp.core.xmpp.StanzaType.Normal
	return when (tmp.toLowerCase()) {
		"chat" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Chat
		"error" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Error
		"get" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Get
		"groupchat" -> org.tigase.jaxmpp.core.xmpp.StanzaType.GroupChat
		"headline" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Headline
		"probe" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Probe
		"result" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Result
		"set" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Set
		"subscribe" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Subscribe
		"subscribed" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Subscribed
		"unavailable" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Unavailable
		"unsubscribe" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Unsubscribe
		"unsubscribed" -> org.tigase.jaxmpp.core.xmpp.StanzaType.Unsubscribed
		else -> org.tigase.jaxmpp.core.xmpp.StanzaType.Normal
	}
}
