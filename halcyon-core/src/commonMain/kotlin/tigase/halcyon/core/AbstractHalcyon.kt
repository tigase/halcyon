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
package tigase.halcyon.core

import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.requests.RequestsManager
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.SessionController
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.PingModule
import tigase.halcyon.core.xmpp.modules.StreamErrorModule
import tigase.halcyon.core.xmpp.modules.StreamFeaturesModule
import tigase.halcyon.core.xmpp.modules.auth.SaslModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule

data class HalcyonStateChangeEvent(
	val oldState: tigase.halcyon.core.AbstractHalcyon.State, val newState: tigase.halcyon.core.AbstractHalcyon.State
) : tigase.halcyon.core.eventbus.Event(
	tigase.halcyon.core.HalcyonStateChangeEvent.Companion.TYPE
) {

	companion object {
		const val TYPE = "tigase.halcyon.core.HalcyonStateChangeEvent"
	}
}

data class TickEvent(val counter: Long, val timestamp: Long) :
	tigase.halcyon.core.eventbus.Event(tigase.halcyon.core.TickEvent.Companion.TYPE) {

	companion object {
		const val TYPE = "tigase.halcyon.core.TickEvent"
	}
}

abstract class AbstractHalcyon : tigase.halcyon.core.Context, tigase.halcyon.core.PacketWriter {

	private val log = tigase.halcyon.core.logger.Logger("tigase.halcyon.core.AbstractHalcyon")

	enum class State {
		Connecting,
		Connected,
		Disconnecting,
		Disconnected
	}

	protected var connector: tigase.halcyon.core.connector.AbstractConnector? = null
	protected var sessionController: SessionController? = null

	private var tickCounter: Long = 0

	final override val sessionObject: tigase.halcyon.core.SessionObject = tigase.halcyon.core.SessionObject()
	final override val eventBus: tigase.halcyon.core.eventbus.EventBus =
		tigase.halcyon.core.eventbus.EventBus(sessionObject)
	override val writer: tigase.halcyon.core.PacketWriter
		get() = this
	final override val modules: tigase.halcyon.core.modules.ModulesManager =
		tigase.halcyon.core.modules.ModulesManager()
	val requestsManager: RequestsManager = RequestsManager()
	val configurator: tigase.halcyon.Configurator = tigase.halcyon.Configurator(sessionObject)
	private val executor = tigase.halcyon.core.excutor.Executor()

	var state = tigase.halcyon.core.AbstractHalcyon.State.Disconnected
		internal set(value) {
			val old = field
			field = value
			eventBus.fire(tigase.halcyon.core.HalcyonStateChangeEvent(old, field))
		}

	init {
		modules.context = this
		eventBus.register<tigase.halcyon.core.connector.ReceivedXMLElementEvent>(tigase.halcyon.core.connector.ReceivedXMLElementEvent.TYPE,
																				 handler = { _, event ->
																					 processReceivedXmlElement(event.element)
																				 })

		eventBus.register<SessionController.StopEverythingEvent>(
			SessionController.StopEverythingEvent.TYPE
		) { sessionObject, event -> disconnect() }

		modules.register(StreamErrorModule())
		modules.register(StreamFeaturesModule())
		modules.register(StreamManagementModule())
		modules.register(SaslModule())
		modules.register(BindModule())
		modules.register(PingModule())
	}

	protected fun processReceivedXmlElement(element: Element) {
		val handled = requestsManager.findAndExecute(element)
		if (handled) return

		val modules = modules.getModulesFor(element)
		if (modules.isEmpty()) {
			log.fine("Unsupported stanza: " + element.getAsString())
			sendErrorBack(element, XMPPException(ErrorCondition.FeatureNotImplemented))
			return
		}

		executor.execute {
			try {
				modules.forEach {
					it.process(element)
				}
			} catch (e: XMPPException) {
				if (log.isLoggable(tigase.halcyon.core.logger.Level.FINEST)) log.log(
					tigase.halcyon.core.logger.Level.FINEST,
					"Error ${e.condition} during processing stanza " + element.getAsString(),
					e
				)
				sendErrorBack(element, e)
			} catch (e: Exception) {
				if (log.isLoggable(tigase.halcyon.core.logger.Level.FINEST)) log.log(
					tigase.halcyon.core.logger.Level.FINEST, "Problem on processing element " + element.getAsString(), e
				)
				sendErrorBack(element, e)
			}
		}
	}

	private fun createError(element: Element, caught: Exception): Element? {
		if (caught is XMPPException) {
			return createError(element, caught.condition, caught.message)
		} else {
			return null
		}
	}

	private fun createError(element: Element, errorCondition: ErrorCondition, msg: String?): Element {
		val resp = element(element.name) {
			attribute("type", "error")
			element.attributes["id"]?.apply {
				attribute("id", this)
			}
			element.attributes["from"]?.apply {
				attribute("to", this)
			}

			"error"{
				errorCondition.type?.let {
					attribute("type", it)
				}
				errorCondition.errorCode?.let {
					attribute("code", it.toString())
				}

				errorCondition.elementName {
					attribute("xmlns", XMPPException.XMLNS)
				}

				msg?.let {
					"text"{
						attribute("xmlns", XMPPException.XMLNS)
						+it
					}
				}

			}

		}
		return resp
	}

	private fun sendErrorBack(element: Element, exception: Exception) {
		when (element.name) {
			"iq", "presence", "message" -> {
				if (element.attributes["type"] == "error") {
					log.fine("Ignoring unexpected error response")
					return
				}
				createError(element, exception)?.apply {
					writeDirectly(this)
				}
			}
			else -> {
				writeDirectly(element("stream:error") {
					"unsupported-stanza-type"{
						xmlns = "urn:ietf:params:xml:ns:xmpp-streams"
					}
				})
				connector?.stop()
			}
		}
	}

	protected abstract fun createConnector(): tigase.halcyon.core.connector.AbstractConnector

	protected fun tick() {
		eventBus.fire(tigase.halcyon.core.TickEvent(++tickCounter, tigase.halcyon.core.currentTimestamp()))
	}

	override fun writeDirectly(element: Element) {
		if (this.connector == null) throw tigase.halcyon.core.exceptions.HalcyonException("Connector is not initialized")
		connector!!.send(element.getAsString())
		eventBus.fire(tigase.halcyon.core.connector.SentXMLElementEvent(element, null))
	}

	override fun write(stanza: Element): Request<*> {
		if (this.connector == null) throw tigase.halcyon.core.exceptions.HalcyonException("Connector is not initialized")
		val request = requestsManager.create(stanza)
		connector!!.send(stanza.getAsString())
		eventBus.fire(tigase.halcyon.core.connector.SentXMLElementEvent(stanza, request))
		return request
	}

	internal fun write(request: Request<*>) {
		if (this.connector == null) throw tigase.halcyon.core.exceptions.HalcyonException("Connector is not initialized")
		connector!!.send(request.requestStanza.getAsString())
		eventBus.fire(tigase.halcyon.core.connector.SentXMLElementEvent(request.requestStanza, request))
	}

	fun connect() {
		modules.initModules()
		log.info("Connecting")
		state = tigase.halcyon.core.AbstractHalcyon.State.Connecting
		try {
			connector = createConnector()
			sessionController = connector!!.createSessionController()

			sessionController!!.start()
			connector!!.start()
		} catch (e: Exception) {
			state = tigase.halcyon.core.AbstractHalcyon.State.Disconnected
			throw e
		}
	}

	fun disconnect() {
		log.info("Disconnecting")
		state = tigase.halcyon.core.AbstractHalcyon.State.Disconnecting
		try {
			sessionController?.stop()
			connector?.stop()
		} finally {
			state = tigase.halcyon.core.AbstractHalcyon.State.Disconnected
		}
	}

	override fun <T : Any> requestBuilder(stanza: Element): RequestBuilder<T> = RequestBuilder<T>(this, stanza)

}