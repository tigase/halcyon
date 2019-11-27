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
package tigase.halcyon.core.xmpp.modules.sm

import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SentXMLElementEvent
import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.StreamFeaturesModule

class StreamManagementModule : tigase.halcyon.core.modules.XmppModule {

	companion object {
		const val XMLNS = "urn:xmpp:sm:3"
		const val TYPE = XMLNS

		/**
		 * Delivered to server.
		 */
		const val Delivered = "$XMLNS#DeliveredToServer"
		const val ACK_ENABLED_KEY = "$XMLNS#ACK_ENABLED"
		const val OUTGOING_STREAM_H_KEY = "$XMLNS#OUTGOING_STREAM_H"
		const val INCOMING_STREAM_H_KEY = "$XMLNS#INCOMING_STREAM_H"
		const val INCOMING_STREAM_H_LAST_SENT_KEY = "$XMLNS#INCOMING_STREAM_H_LAST_SENT"
		const val TURNED_ON_KEY = "$XMLNS#TURNED_ON"
		const val RESUME_KEY = "$XMLNS#RESUME"
		const val RESUMPTION_ID_KEY = "$XMLNS#RESUMPTION_ID"
		const val RESUMPTION_TIME_KEY = "$XMLNS#RESUMPTION_TIME"

		fun isAckEnable(sessionObject: tigase.halcyon.core.SessionObject) =
			sessionObject.getProperty<Boolean>(ACK_ENABLED_KEY) ?: false
	}

	class StreamManagementEnabledEvent(val id: String, val resume: Boolean, val mx: Long?) :
		tigase.halcyon.core.eventbus.Event(TYPE) {

		companion object {
			const val TYPE = "tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule.StreamManagementEnabledEvent"
		}
	}

	class StreamManagementFailedEvent(val error: ErrorCondition) : tigase.halcyon.core.eventbus.Event(TYPE) {
		companion object {
			const val TYPE = "tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule.StreamManagementFailedEvent"
		}
	}

	class StreamResumedEvent(val h: Long, val prevId: String) : tigase.halcyon.core.eventbus.Event(TYPE) {
		companion object {
			const val TYPE = "tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule.StreamResumedEvent"
		}
	}

	override val type = TYPE
	override lateinit var context: tigase.halcyon.core.Context
	override val features = arrayOf(XMLNS)
	override val criteria = tigase.halcyon.core.modules.Criterion.xmlns(XMLNS)

	private val log = tigase.halcyon.core.logger.Logger("tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule")

	private val queue = ArrayList<Any>()

	override fun initialize() {
		context.eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE, handler = { _, event ->
			processElementSent(event.element, event.request)
		})
		context.eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE, handler = { _, event ->
			processElementReceived(event.element)
		})
		context.eventBus.register<tigase.halcyon.core.TickEvent>(
			tigase.halcyon.core.TickEvent.TYPE,
			handler = { _, event -> onTick() })
	}

	private fun onTick() {
		if (isAckEnable(context.sessionObject)) {
			if (queue.size > 0) request()
			sendAck(false)
		}
	}

	private fun processElementReceived(element: Element) {
		if (!isAckEnable(context.sessionObject)) return
		if (element.xmlns == XMLNS) return
		increment(INCOMING_STREAM_H_KEY)
	}

	private fun processElementSent(element: Element, request: Request<*>?) {
		if (!isAckEnable(context.sessionObject)) return
		if (element.xmlns == XMLNS) return

		if (request != null) {
			queue.add(request)
		} else {
			queue.add(element)
		}
		increment(OUTGOING_STREAM_H_KEY)
	}

	fun reset() {
		context.sessionObject.setProperty(TURNED_ON_KEY, false)
		context.sessionObject.setProperty(ACK_ENABLED_KEY, false)
		context.sessionObject.setProperty(RESUME_KEY, null)
		context.sessionObject.setProperty(RESUMPTION_ID_KEY, null)
		context.sessionObject.setProperty(INCOMING_STREAM_H_KEY, null)
		context.sessionObject.setProperty(OUTGOING_STREAM_H_KEY, null)
	}

	private fun processFailed(element: Element) {
		reset()

		val e = ErrorCondition.getByElementName(element.getChildrenNS(XMPPException.XMLNS).first().name)
		context.eventBus.fire(StreamManagementFailedEvent(e))
	}

	private fun processEnabled(element: Element) {
		val id = element.attributes["id"]!!
		val resume = element.attributes["resume"]?.toBoolean() ?: false
		val mx = element.attributes["max"]?.toLong()


		context.sessionObject.setProperty(ACK_ENABLED_KEY, true)
		context.sessionObject.setProperty(TURNED_ON_KEY, true)
		context.sessionObject.setProperty(RESUME_KEY, resume)
		context.sessionObject.setProperty(RESUMPTION_ID_KEY, id)
		if (mx != null) context.sessionObject.setProperty(RESUMPTION_TIME_KEY, mx)

		context.eventBus.fire(StreamManagementEnabledEvent(id, resume, mx))
	}

	/**
	 * Process ACK answer from server.
	 */
	private fun processAckResponse(element: Element) {
		val h = element.attributes["h"]?.toLong() ?: 0
		var lh = context.sessionObject.getProperty<Long>(OUTGOING_STREAM_H_KEY) ?: 0

		if (log.isLoggable(tigase.halcyon.core.logger.Level.FINE)) log.fine("Expected h=$lh, received h=$h")

		if (lh >= h) {
			lh = context.sessionObject.getProperty<Long>(OUTGOING_STREAM_H_KEY) ?: 0
			val left = lh - h
			while (queue.size > left) {
				val x = queue.get(0)
				queue.remove(x)
				if (x is Request<*>) {
					x.setData(Delivered, true)
					log.fine("Marked as 'delivered to server': $x")
				}
			}
		}
	}

	fun sendAck() {
		sendAck(false)
	}

	/**
	 * Process ACK request from server.
	 */
	fun sendAck(force: Boolean) {
		var h = context.sessionObject.getProperty<Long>(INCOMING_STREAM_H_KEY) ?: 0
		var lastH = context.sessionObject.getProperty<Long>(INCOMING_STREAM_H_LAST_SENT_KEY) ?: 0

		if (!force && h == lastH) return

		context.sessionObject.setProperty(INCOMING_STREAM_H_LAST_SENT_KEY, h)
		context.writer.writeDirectly(element("a") {
			xmlns = XMLNS
			attribute("h", h.toString())
		})
	}

	private fun processResumed(element: Element) {
		val h = element.attributes["h"]?.toLong() ?: 0

		val unacked = mutableListOf<Any>()
		var lh = context.sessionObject.getProperty<Long>(OUTGOING_STREAM_H_KEY) ?: 0
		val left = lh - h
		if (left > 0) while (queue.size > left) {
			val x = queue.get(0)
			queue.remove(x)
		}
		context.sessionObject.setProperty(OUTGOING_STREAM_H_KEY, h)
		unacked.addAll(queue)
		queue.clear()

		unacked.forEach {
			when (it) {
				is Request<*> -> context.writer.write(it.requestStanza)
				is Element -> context.writer.write(it)
			}
		}

	}

	private fun increment(key: String): Long {
		var v = context.sessionObject.getProperty<Long>(key)
		if (v == null) {
			v = 0
		}
		++v
		context.sessionObject.setProperty(key, v)
		return v
	}

	fun isSupported(): Boolean = StreamFeaturesModule.isFeatureAvailable(context.sessionObject, "sm", XMLNS)

	fun enable() {
		if (isSupported()) {
			context.writer.writeDirectly(element("enable") {
				xmlns = XMLNS
				attribute("resume", "false")
			})
		}
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
		log.fine("Sending ACK request")
		context.writer.writeDirectly(element("r") { xmlns = XMLNS })
	}

	fun resume() {
		var h = context.sessionObject.getProperty<Long>(INCOMING_STREAM_H_KEY) ?: 0
		var id = context.sessionObject.getProperty<String>(RESUMPTION_ID_KEY)

		context.writer.writeDirectly(element("resume") {
			xmlns = XMLNS
			attribute("h", h.toString())
			if (id != null) attribute("id", id)
		})
	}

	fun report() {
		println(
			"""
			SM Report
			---------
			
			Enabled: ${isAckEnable(context.sessionObject)}
			Outgoing stream H: ${context.sessionObject.getProperty<Long>(OUTGOING_STREAM_H_KEY)}
			Incoming stream H: ${context.sessionObject.getProperty<Long>(INCOMING_STREAM_H_KEY)}
			Incoming stream LAST KEY: ${context.sessionObject.getProperty<Long>(INCOMING_STREAM_H_LAST_SENT_KEY)}
			 
			Queue size: ${queue.size}
			Queue: ${queue}
			
			---------
		""".trimIndent()
		)
	}

}