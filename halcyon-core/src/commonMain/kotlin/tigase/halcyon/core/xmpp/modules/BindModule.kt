/*
 * halcyon-core
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
package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.Scope
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toJID

sealed class BindEvent : Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.BindEvent"
	}

	data class Success(val jid: JID) : BindEvent()
	data class Failure(val error: Throwable) : BindEvent()

}

class BindModule(override val context: AbstractHalcyon) : XmppModule {

	enum class State {

		Unknown,
		InProgress,
		Success,
		Failed
	}

	companion object {

		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-bind"
		const val TYPE = XMLNS
	}

	override val type = TYPE
	override val criteria: Criteria? = null
	override val features = arrayOf(XMLNS)

	@Deprecated("Moved to Context")
	var boundJID: JID? by context::boundJID
		internal set

	var state: State by propertySimple(Scope.Session, State.Unknown)
		internal set

	override fun initialize() {}

	fun boundAs(resource: String? = null): RequestBuilder<BindResult, IQ> {
		val stanza = iq {
			type = IQType.Set
			"bind" {
				xmlns = XMLNS
				resource?.let {
					"resource" {
						value = it
					}
				}
			}
		}
		return context.request.iq(stanza)
			.onSend { state = State.InProgress }
			.map(this::createBindResult)
			.response {
				it.onSuccess { success ->
					state = State.Success
					context.boundJID = success.jid
					context.eventBus.fire(BindEvent.Success(success.jid))
				}
				it.onFailure {
					state = State.Failed
					context.eventBus.fire(BindEvent.Failure(it))
				}
			}
	}

	private fun createBindResult(element: IQ): BindResult {
		val bind = element.getChildrenNS("bind", XMLNS)!!
		val jidElement = bind.getFirstChild("jid")!!
		val jid = JID.parse(jidElement.value!!)
		return BindResult(jid)
	}

	override fun process(element: Element) {
		throw XMPPException(ErrorCondition.BadRequest)
	}

	internal fun boundAs(jid: String) {
		context.boundJID = jid.toJID()
		context.eventBus.fire(BindEvent.Success(context.boundJID!!))
	}

	data class BindResult(val jid: JID)

}