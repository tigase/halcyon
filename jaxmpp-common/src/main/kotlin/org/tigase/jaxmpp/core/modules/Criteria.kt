package org.tigase.jaxmpp.core.modules

import org.tigase.jaxmpp.core.xml.Element

interface Criteria {

	fun match(element: Element): Boolean

}