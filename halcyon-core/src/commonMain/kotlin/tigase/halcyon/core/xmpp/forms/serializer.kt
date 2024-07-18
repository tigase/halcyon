package tigase.halcyon.core.xmpp.forms

import tigase.halcyon.core.xml.ElementWrapperSerializer

object JabberDataFormSerializer :
    ElementWrapperSerializer<JabberDataForm>(elementProvider = { it.element },
        objectFactory = { JabberDataForm(it) }
    )
