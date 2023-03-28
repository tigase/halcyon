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
import tigase.halcyon.core.Context
import tigase.halcyon.core.Scope
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.auth.*
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toJID

sealed class BindEvent : Event(TYPE) {

	companion object : EventDefinition<BindEvent> {

		override val TYPE = "tigase.halcyon.core.xmpp.modules.BindEvent"
	}

	data class Success(val jid: JID) : BindEvent()
	data class Failure(val error: Throwable) : BindEvent()

}

@HalcyonConfigDsl
interface BindModuleConfig {

	var resource: String?
}

class BindModule(override val context: AbstractHalcyon) : XmppModule, InlineProtocol, BindModuleConfig {

	enum class State {

		Unknown,
		InProgress,
		Success,
		Failed
	}

	companion object : XmppModuleProvider<BindModule, BindModuleConfig> {

		const val BIND2_XMLNS = "urn:xmpp:bind:0"
		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-bind"
		override val TYPE = XMLNS
		override fun configure(module: BindModule, cfg: BindModuleConfig.() -> Unit) {
			module.cfg()
		}

		override fun instance(context: Context): BindModule = BindModule(context as AbstractHalcyon)
	}

	override val type = TYPE
	override val criteria: Criteria? = null
	override val features = arrayOf(XMLNS)

	@Deprecated("Moved to Context")
	var boundJID: JID? by context::boundJID
		internal set

	var state: State by propertySimple(Scope.Session, State.Unknown)
		internal set

	override var resource: String? = null

	override fun initialize() {}

	fun bind(resource: String? = this.resource): RequestBuilder<BindResult, IQ> {
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
					bind(success.jid)
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

	private fun bind(jid: JID) {
		state = State.Success
		context.boundJID = jid
		context.eventBus.fire(BindEvent.Success(jid))
	}

	data class BindResult(val jid: JID)

	override fun featureFor(features: InlineFeatures, stage: InlineProtocolStage): Element? {
		return if (stage == InlineProtocolStage.AfterSasl) {
			val isResumptionAvailable =
				context.getModuleOrNull(StreamManagementModule)?.resumptionContext?.isResumptionAvailable() ?: false

			if (!isResumptionAvailable) {
				val bindInlineFeatures = features.subInline("bind", BIND2_XMLNS)
				element("bind") {
					xmlns = BIND2_XMLNS
					"tag" { +"Halcyon" }
					context.modules.getModules()
						.filterIsInstance<InlineProtocol>()
						.mapNotNull { it.featureFor(bindInlineFeatures, InlineProtocolStage.AfterBind) }
						.forEach { addChild(it) }
				}
			} else null
		} else null
	}

	override fun process(response: InlineResponse) {
		response.whenExists(InlineProtocolStage.AfterSasl, "bound", BIND2_XMLNS) { boundElement ->
			bind(response.element.getFirstChild("authorization-identifier")!!.value!!.toJID())

			InlineResponse(InlineProtocolStage.AfterBind, boundElement).let { response ->
				context.modules.getModules()
					.filterIsInstance<InlineProtocol>()
					.forEach { consumer ->
						consumer.process(response)
					}
			}

		}
	}

}