import tigase.halcyon.core.xmpp.JID

fun tigase.halcyon.core.xml.Element.getFromAttr(): JID? {
	val tmp = this.attributes["from"] ?: return null
	return JID.parse(tmp)
}

fun tigase.halcyon.core.xml.Element.getToAttr(): JID? {
	val tmp = this.attributes["to"] ?: return null
	return JID.parse(tmp)
}

fun tigase.halcyon.core.xml.Element.getIdAttr(): String? {
	return this.attributes["id"]
}

fun tigase.halcyon.core.xml.Element.getTypeAttr(): tigase.halcyon.core.xmpp.StanzaType {
	val tmp = this.attributes["type"] ?: return tigase.halcyon.core.xmpp.StanzaType.Normal
	return when (tmp.toLowerCase()) {
		"chat" -> tigase.halcyon.core.xmpp.StanzaType.Chat
		"error" -> tigase.halcyon.core.xmpp.StanzaType.Error
		"get" -> tigase.halcyon.core.xmpp.StanzaType.Get
		"groupchat" -> tigase.halcyon.core.xmpp.StanzaType.GroupChat
		"headline" -> tigase.halcyon.core.xmpp.StanzaType.Headline
		"probe" -> tigase.halcyon.core.xmpp.StanzaType.Probe
		"result" -> tigase.halcyon.core.xmpp.StanzaType.Result
		"set" -> tigase.halcyon.core.xmpp.StanzaType.Set
		"subscribe" -> tigase.halcyon.core.xmpp.StanzaType.Subscribe
		"subscribed" -> tigase.halcyon.core.xmpp.StanzaType.Subscribed
		"unavailable" -> tigase.halcyon.core.xmpp.StanzaType.Unavailable
		"unsubscribe" -> tigase.halcyon.core.xmpp.StanzaType.Unsubscribe
		"unsubscribed" -> tigase.halcyon.core.xmpp.StanzaType.Unsubscribed
		else -> tigase.halcyon.core.xmpp.StanzaType.Normal
	}
}
