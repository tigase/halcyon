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
package tigase.halcyon.core.xmpp.modules.sm

import kotlinx.serialization.Serializable
import tigase.halcyon.core.ClearedEvent
import tigase.halcyon.core.Context
import tigase.halcyon.core.Scope
import tigase.halcyon.core.TickEvent
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SentXMLElementEvent
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.ModulesManager
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.auth.*
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.Presence

@HalcyonConfigDsl
interface StreamManagementModuleConfig

class StreamManagementModule(override val context: Context) : XmppModule, InlineProtocol, StreamManagementModuleConfig {

	@Serializable
	class ResumptionContext {

		internal var isActive: Boolean = false

		var resumptionTime: Long = 0
			internal set

		var incomingH: Long = 0L
			internal set

		var outgoingH: Long = 0L
			internal set

		var incomingLastSentH: Long = 0L
			internal set

		var isAckEnabled: Boolean = false
			internal set

		var resID: String? = null
			internal set

		var isResumeEnabled: Boolean = false
			internal set

		var location: String? = null
			internal set

		fun isResumptionAvailable() = resID != null && isResumeEnabled

		/**
		 * Returns ```true``` if ACK is enabled and currently active.
		 */
		val isAckActive: Boolean
			get() = isAckEnabled && isActive

	}

	companion object : XmppModuleProvider<StreamManagementModule, StreamManagementModuleConfig> {

		const val XMLNS = "urn:xmpp:sm:3"
		override val TYPE = XMLNS
		override fun instance(context: Context): StreamManagementModule = StreamManagementModule(context)

		override fun configure(module: StreamManagementModule, cfg: StreamManagementModuleConfig.() -> Unit) =
			module.cfg()

		override fun doAfterRegistration(module: StreamManagementModule, moduleManager: ModulesManager) =
			module.initialize()

	}

	var resumptionContext: ResumptionContext by property(Scope.Session) { ResumptionContext() }

	sealed class StreamManagementEvent : Event(TYPE) {

		companion object : EventDefinition<StreamManagementEvent> {

			override val TYPE = "tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule.StreamManagementEvent"
		}

		class Enabled(val id: String, val resume: Boolean, val mx: Long?) : StreamManagementEvent()
		class Failed(val error: ErrorCondition) : StreamManagementEvent()
		class Resumed(val h: Long, val prevId: String) : StreamManagementEvent()
	}

	override val type = TYPE
	override val features = arrayOf(XMLNS)
	override val criteria = Criterion.xmlns(XMLNS)

	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule")

	private val queue = ArrayList<Any>()

	private fun initialize() {
		context.eventBus.register(SentXMLElementEvent) { event ->
			processElementSent(event.element, event.request)
		}
		context.eventBus.register(ReceivedXMLElementEvent) { event ->
			processElementReceived(event.element)
		}
		context.eventBus.register(ClearedEvent) {
			if (it.scopes.contains(Scope.Connection)) {
				log.fine { "Disabling ACK" }
				resumptionContext.isActive = false
			}
		}
		context.eventBus.register(TickEvent) { onTick() }
	}

	private fun onTick() {
		if (resumptionContext.isAckActive) {
			if (queue.size > 0) request()
			sendAck(false)
		}
	}

	private inline fun isElementCounted(element: Element) =
		element.xmlns != XMLNS && (element.name == Message.NAME || element.name == IQ.NAME || element.name == Presence.NAME)

	private fun processElementReceived(element: Element) {
		if (!resumptionContext.isAckActive) return
		if (!isElementCounted(element)) return

		++resumptionContext.incomingH
	}

	private fun processElementSent(element: Element, request: Request<*, *>?) {
		if (!resumptionContext.isAckActive) return
		if (!isElementCounted(element)) return

		if (request != null) {
			queue.add(request)
		} else {
			queue.add(element)
		}
		++resumptionContext.outgoingH
	}

	fun reset() {
		queue.clear()
	}

	private fun processFailed(element: Element) {
		reset()
		val e = ErrorCondition.getByElementName(
			element.getChildrenNS(XMPPException.XMLNS).first().name
		)
		context.eventBus.fire(StreamManagementEvent.Failed(e))
	}

	private fun processEnabled(element: Element) {
		val id = element.attributes["id"]!!
		val location = element.attributes["location"]
		val resume = element.attributes["resume"]?.toBoolean() ?: false
		// server's preferred maximum resumption time
		val mx = element.attributes["max"]?.toLong() ?: 0

		resumptionContext.let { ctx ->
			ctx.resID = id
			ctx.isResumeEnabled = resume
			ctx.location = location
			ctx.resumptionTime = mx
			ctx.isAckEnabled = true
			ctx.isActive = true
		}

		context.eventBus.fire(StreamManagementEvent.Enabled(id, resume, mx))
	}

	/**
	 * Process ACK answer from server.
	 */
	private fun processAckResponse(element: Element) {
		val h = element.attributes["h"]?.toLong() ?: 0
		var lh = resumptionContext.outgoingH

		log.fine { "Expected h=$lh, received h=$h, queue=${queue.size}" }

		if (lh >= h) {
			lh = resumptionContext.outgoingH
			val left = lh - h
			markAsDeliveredAndRemoveFromQueue(left)
		}
	}

	fun sendAck() {
		sendAck(false)
	}

	/**
	 * Process ACK request from server.
	 */
	fun sendAck(force: Boolean) {
		val h = resumptionContext.incomingH
		val lastH = resumptionContext.incomingLastSentH

		if (!force && h == lastH) return

		resumptionContext.incomingLastSentH = h
		context.writer.writeDirectly(element("a") {
			xmlns = XMLNS
			attribute("h", h.toString())
		})
	}

	private fun markAsDeliveredAndRemoveFromQueue(left: Long) {
		while (queue.size > left) {
			val x = queue.get(0)
			queue.remove(x)
			if (x is Request<*, *>) {
				x.markAsSent()
				log.fine { "Marked as 'delivered to server': $x" }
			}
		}
	}

	private fun processResumed(element: Element) {
		val ctx = resumptionContext
		val h = element.attributes["h"]?.toLong() ?: 0
		val id = element.attributes["previd"] ?: ""

		val unacked = mutableListOf<Any>()
		val lh = ctx.outgoingH
		val left = lh - h
		if (left > 0) markAsDeliveredAndRemoveFromQueue(left)
		ctx.outgoingH = h
		unacked.addAll(queue)
		queue.clear()

		unacked.forEach {
			when (it) {
				is Request<*, *> -> context.writer.write(it)
				is Element -> context.writer.writeDirectly(it)
			}
		}
		ctx.isActive = true
		context.eventBus.fire(StreamManagementEvent.Resumed(h, id))
	}

	fun enable() {
		context.writer.writeDirectly(element("enable") {
			xmlns = XMLNS
			attribute("resume", "true")
		})
	}

	override fun process(element: Element) {
		when (element.name) {
			"r" -> sendAck(true)
			"a" -> processAckResponse(element)
			"enabled" -> processEnabled(element)
			"resumed" -> processResumed(element)
			"failed" -> processFailed(element)
			else -> throw XMPPException(ErrorCondition.FeatureNotImplemented)
		}
	}

	fun request() {
		if (resumptionContext.isAckActive) {
			log.fine { "Sending ACK request" }
			context.writer.writeDirectly(element("r") { xmlns = XMLNS })
		}
	}

	fun resume() {
		val h = resumptionContext.incomingH
		val id = resumptionContext.resID ?: throw HalcyonException("Cannot resume session: no resumption ID")

		context.writer.writeDirectly(element("resume") {
			xmlns = XMLNS
			attribute("h", h.toString())
			attribute("previd", id)
		})
	}

	override fun featureFor(features: InlineFeatures, stage: InlineProtocolStage): Element? {
		return when (stage) {
			InlineProtocolStage.AfterSasl -> {
				if (resumptionContext.isResumptionAvailable() && features.supports("sm", XMLNS)) {
					val h = resumptionContext.incomingH
					val id =
						resumptionContext.resID ?: throw HalcyonException("Cannot resume session: no resumption ID")
					element("resume") {
						xmlns = XMLNS
						attribute("h", h.toString())
						attribute("previd", id)
					}
				} else null
			}

			InlineProtocolStage.AfterBind -> {
				if (features.supports(XMLNS)) element("enable") {
					xmlns = XMLNS
					attribute("resume", "true")
				} else null
			}
		}
	}

	override fun process(response: InlineResponse) {
		response.whenExists(InlineProtocolStage.AfterSasl, "resumed", XMLNS) { processResumed(it) }
		response.whenExists(InlineProtocolStage.AfterBind, "enabled", XMLNS) { processEnabled(it) }
	}

}