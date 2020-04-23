/*
 * Tigase Halcyon XMPP Library
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core.xmpp.modules.mam

import tigase.halcyon.core.Context
import tigase.halcyon.core.currentTimestamp
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.parseISO8601
import tigase.halcyon.core.requests.IQRequestBuilder
import tigase.halcyon.core.timestampToISO8601
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.IdGenerator
import tigase.halcyon.core.xmpp.forms.FieldType
import tigase.halcyon.core.xmpp.forms.FormType
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.modules.RSM
import tigase.halcyon.core.xmpp.modules.parseRSM
import tigase.halcyon.core.xmpp.stanzas.*

data class MAMMessageEvent(
	val resultStanza: Message, val queryId: String, val id: String, val forwardedStanza: ForwardedStanza<Message>
) : Event(TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.xmpp.modules.mam.MAMMessageEvent"
	}
}

class ForwardedStanza<TYPE : Stanza<*>>(private val element: Element) : Element by element {

	val timestamp: Long? by lazy(this::getXmppDelay)

	val stanza: TYPE
		get() = getForwardedStanza()

	private fun getXmppDelay(): Long? {
		return element.getChildrenNS("delay", "urn:xmpp:delay")?.let {
			it.attributes["stamp"]?.let { stamp -> parseISO8601(stamp) }
		}
	}

	private fun getForwardedStanza(): TYPE {
		val e = element.getFirstChild("message")!!
		return wrap(e)
	}

}

class MAMModule(override val context: Context) : XmppModule {

	data class Fin(val complete: Boolean = false, val rsm: RSM?)

	private data class RegisteredQuery(val queryId: String, val createdTimestamp: Long, var validUntil: Long)

	companion object {
		const val XMLNS = "urn:xmpp:mam:2"
		const val TYPE = XMLNS
	}

	override val type = TYPE
	override val criteria: Criteria? = Criterion.chain(
		Criterion.name(Message.NAME), Criterion.nameAndXmlns("result", XMLNS)
	)
	override val features = arrayOf(XMLNS)

	private val requests = ExpiringMap<String, RegisteredQuery>()

	override fun initialize() {
		requests.expirationChecker = {
			it.validUntil < currentTimestamp()
		}
		requests.eventBus = context.eventBus
	}

	override fun process(element: Element) {
		val result = element.getChildrenNS("result", XMLNS) ?: return
		val queryId = result.attributes["queryid"] ?: return
		val query = requests.get(queryId) ?: return
		val resultId = result.attributes["id"] ?: return

		val forwarded = result.getChildrenNS("forwarded", "urn:xmpp:forward:0") ?: return

		val msg = forwarded.getFirstChild("message") ?: return

		context.eventBus.fire(
			MAMMessageEvent(
				wrap(element), queryId, resultId, ForwardedStanza(forwarded)
			)
		)
	}

	private fun prepareForm(with: String? = null, start: Long? = null, end: Long? = null): Element? {
		val form = JabberDataForm(FormType.Submit)
		form.addField("FORM_TYPE", FieldType.Hidden).fieldValue = "urn:xmpp:mam:2"

		if (start != null) form.addField("start", FieldType.TextSingle).fieldValue = timestampToISO8601(start)
		if (end != null) form.addField("end", FieldType.TextSingle).fieldValue = timestampToISO8601(end)
		if (with != null) form.addField("with", FieldType.JidSingle).fieldValue = with

		return if (form.element.children.size > 1) form.createSubmitForm() else null
	}

	fun query(rsm: RSM? = null, with: String? = null, start: Long? = null, end: Long? = null): IQRequestBuilder<Fin> {
		val queryId = IdGenerator.nextId()
		val form: Element? = prepareForm(with, start, end)
		val stanza = iq {
			type = IQType.Set
			query(XMLNS) {
				attribute("queryid", queryId)
				if (form != null) addChild(form)
				if (rsm != null) addChild(rsm.toElement())
			}
		}
		val q = RegisteredQuery(queryId, currentTimestamp(), currentTimestamp() + 30000)
		requests.put(queryId, q)


		return context.request.iq(stanza).resultBuilder { element -> createResponse(element, q) }
	}

	private fun createResponse(responseStanza: Element, registeredQuery: RegisteredQuery): Fin {
		val fin = responseStanza.getChildrenNS("fin", XMLNS)
		registeredQuery.validUntil = currentTimestamp() + 10000
		val rsm: RSM? = fin?.getChildrenNS(RSM.NAME, RSM.XMLNS)?.let(::parseRSM)
		return Fin(complete = fin?.attributes?.get("complete").toBool(), rsm = rsm)
	}

	private fun String?.toBool(): Boolean {
		return when (this) {
			"1", "true" -> true
			else -> false
		}
	}

}