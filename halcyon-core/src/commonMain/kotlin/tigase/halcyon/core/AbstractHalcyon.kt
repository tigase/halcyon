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
package tigase.halcyon.core

import tigase.halcyon.core.builder.ConfigurationBuilder
import tigase.halcyon.core.configuration.Configuration
import tigase.halcyon.core.connector.*
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventBus
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.eventbus.EventHandler
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.*
import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.requests.RequestBuilderFactory
import tigase.halcyon.core.requests.RequestsManager
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.FullJID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.auth.SASLContext
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType

data class HalcyonStateChangeEvent(val oldState: AbstractHalcyon.State, val newState: AbstractHalcyon.State) :
    Event(TYPE) {

    companion object : EventDefinition<HalcyonStateChangeEvent> {

        override val TYPE = "tigase.halcyon.core.HalcyonStateChangeEvent"
    }
}

data class TickEvent(val counter: Long) : Event(TYPE) {

    companion object : EventDefinition<TickEvent> {

        override val TYPE = "tigase.halcyon.core.TickEvent"
    }
}

@Suppress("LeakingThis")
abstract class AbstractHalcyon(configurator: ConfigurationBuilder) : Context, PacketWriter {

    var running: Boolean = false
        private set

    private val log = LoggerFactory.logger("tigase.halcyon.core.AbstractHalcyon")

    enum class State {

        Connecting, Connected, Disconnecting, Disconnected, Stopped
    }

    internal var connector: AbstractConnector? = null
    protected var sessionController: SessionController? = null
    final override val eventBus: EventBus = EventBus(this)
    override val authContext: SASLContext by property(Scope.Connection) { SASLContext() }
    override var boundJID: FullJID? by propertySimple(Scope.Session, null)

    var autoReconnect: Boolean = true

    override val request = RequestBuilderFactory(this)

    override val writer: PacketWriter
        get() = this
    final override val modules: ModulesManager = ModulesManager()
    val internalDataStore = InternalDataStore()
    val requestsManager: RequestsManager = RequestsManager()
    private val executor = tigase.halcyon.core.excutor.Executor()
    override val config: Configuration
    var state = State.Stopped
        internal set(value) {
            val old = field
            field = value
            if (old != field) eventBus.fire(HalcyonStateChangeEvent(old, field))
        }

    init {
        modules.context = this
        this.config = configurator.build()

        eventBus.register(ReceivedXMLElementEvent, ::processReceivedXmlElementEvent)
        eventBus.register(SessionController.SessionControllerEvents, ::onSessionControllerEvent)
        eventBus.register<TickEvent>(TickEvent) { requestsManager.findOutdated() }

        configurator.modulesConfigBuilder.initializeModules(modules)
    }

    protected open fun onSessionControllerEvent(event: SessionController.SessionControllerEvents) {
        when (event) {
            is SessionController.SessionControllerEvents.ErrorStop, is SessionController.SessionControllerEvents.ErrorReconnect -> processControllerErrorEvent(
                event
            )

            is SessionController.SessionControllerEvents.Successful -> onSessionEstablished()
        }
    }

    private fun onSessionEstablished() {
        log.info("Session established")
        state = State.Connected
        requestsManager.boundJID = boundJID
    }

    private fun processControllerErrorEvent(event: SessionController.SessionControllerEvents) {
        if (event is SessionController.SessionControllerEvents.ErrorReconnect && (this.autoReconnect || event.force)) {
            state = State.Disconnected
            stopConnector {
                reconnect(event.immediately)
            }
        } else {
            disconnect()
        }
    }

    abstract fun reconnect(immediately: Boolean = false)

    protected fun processReceivedXmlElementEvent(event: ReceivedXMLElementEvent) {
        processReceivedXmlElement(event.element)
    }

    internal fun processReceivedXmlElement(receivedElement: Element) {
        modules.processReceiveInterceptors(receivedElement) {
            it.onSuccess { element ->
                if (element == null) return@onSuccess
                val handled = requestsManager.findAndExecute(element)
                if (element.name == IQ.NAME && (handled || (element.attributes["type"] == IQType.Result.value || element.attributes["type"] == IQType.Error.value))) return@onSuccess


                val modules = modules.getModulesFor(element)
                if (modules.isEmpty()) {
                    log.fine { "Unsupported stanza: " + element.getAsString(3, false) }
                    sendErrorBack(element, XMPPException(ErrorCondition.FeatureNotImplemented))
                    return@onSuccess
                }

                executor.execute {
                    try {
                        modules.forEach {
                            it.process(element)
                        }
                    } catch (e: XMPPException) {
                        log.finest(e) { "Error ${e.condition} during processing stanza ${element.getAsString(showValue = false)}" }
                        sendErrorBack(element, e)
                    } catch (e: Exception) {
                        log.finest(e) { "Problem on processing element ${element.getAsString(showValue = false)}" }
                        sendErrorBack(element, e)
                    }
                }
            }
            it.onFailure { e ->
                log.info(e) { "Problem on processing element ${receivedElement.getAsString(showValue = false)}" }
                sendErrorBack(receivedElement, e)
            }
        }
    }

    private fun createError(element: Element, caught: Throwable): Element? {
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

            "error" {
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
                    "text" {
                        attribute("xmlns", XMPPException.XMLNS)
                        +it
                    }
                }
            }
        }
        return resp
    }

    private fun sendErrorBack(element: Element, exception: Throwable) {
        when (element.name) {
            "iq", "presence", "message" -> {
                if (element.attributes["type"] == "error") {
                    log.fine { "Ignoring unexpected error response" }
                    return
                }
                createError(element, exception)?.apply {
                    writeDirectly(this)
                }
            }

            else -> {
                writeDirectly(element("stream:error") {
                    "unsupported-stanza-type" {
                        xmlns = "urn:ietf:params:xml:ns:xmpp-streams"
                    }
                })
                connector?.stop()
            }
        }
    }

    protected abstract fun createConnector(): AbstractConnector

    protected fun getConnectorState(): tigase.halcyon.core.connector.State =
        this.connector?.state ?: tigase.halcyon.core.connector.State.Disconnected

    private fun logSendingStanza(element: Element) {
        when {
            log.isLoggable(Level.FINEST) -> log.finest("Sending: ${element.getAsString()}")
            log.isLoggable(Level.FINER) -> log.finer("Sending: ${element.getAsString(deep = 3, showValue = false)}")
            log.isLoggable(Level.FINE) -> log.fine("Sending: ${element.getAsString(deep = 2, showValue = false)}")
        }
    }

    override fun writeDirectly(stanza: Element) {
        val c = this.connector ?: throw HalcyonException("Connector is not initialized")
        if (c.state != tigase.halcyon.core.connector.State.Connected) throw HalcyonException("Connector is not connected ${c.state}")
        modules.processOutgoingFilters(stanza) {
            it.onSuccess { toSend ->
                if (toSend == null) return@onSuccess
                logSendingStanza(toSend)
                c.send(toSend.getAsString())
                eventBus.fire(SentXMLElementEvent(toSend, null))
            }
            it.onFailure {
                log.warning(it) { "Problem on filtering stanza ${stanza.getAsString()}" }
            }
        }
    }

    override fun write(request: Request<*, *>) {
        val c = this.connector ?: throw HalcyonException("Connector is not initialized")
        if (c.state != tigase.halcyon.core.connector.State.Connected) throw HalcyonException("Connector is not connected")
        modules.processOutgoingFilters(request.stanza) {
            it.onSuccess { element ->
                if (element == null) return@onSuccess
                requestsManager.register(request)
                logSendingStanza(element)
                c.send(element.getAsString())

                if (getModuleOrNull(StreamManagementModule)?.resumptionContext?.isAckActive != true) {
                    request.markAsSent()
                }
                eventBus.fire(SentXMLElementEvent(request.stanza, request))
            }
            it.onFailure {
                log.warning(it) { "Problem on filtering stanza $request" }
            }

        }
    }

    protected open fun onConnecting() {}

    protected open fun onDisconnecting() {}

    fun <T : HalcyonModule> getModule(type: String): T = modules.getModule(type)
    fun <T : HalcyonModule> getModule(provider: HalcyonModuleProvider<T, out Any>): T = modules.getModule(provider.TYPE)

    fun <T : HalcyonModule> getModuleOrNull(type: String): T? = modules.getModuleOrNull(type)
    fun <T : HalcyonModule> getModuleOrNull(provider: HalcyonModuleProvider<T, out Any>): T? =
        modules.getModuleOrNull(provider.TYPE)

    @ReflectionModuleManager
    inline fun <reified T : HalcyonModule> getModule(): T = modules.getModule(T::class)

    protected fun startConnector() {
        if (running) {
            log.fine { "Starting connector" }

            stopConnector()

            sessionController?.stop()
            sessionController = null
            connector = createConnector()
            sessionController = connector!!.createSessionController()

            sessionController!!.start()
            connector!!.start()
        } else throw HalcyonException("Client is not running")
    }

    protected fun stopConnector(doAfterDisconnected: (() -> Unit)? = null) {
        if (connector != null || sessionController != null) {
            log.fine { "Stopping connector${if (doAfterDisconnected != null) " (with action after disconnect)" else ""}" }
            if (doAfterDisconnected != null) connector?.let {
                waitForDisconnect(it, doAfterDisconnected)
            }
            if (!running) {
                sessionController?.stop()
                sessionController = null
            }
            connector?.stop()
            connector = null
        }
    }

    protected fun waitForDisconnect(connector: AbstractConnector?, handler: () -> Unit) {
        log.finer { "Waiting for disconnection" }
        if (connector == null) {
            log.finest { "No connector. Calling handler." }
            handler.invoke()
        } else {
            var fired = false
            val h: EventHandler<ConnectorStateChangeEvent> = object : EventHandler<ConnectorStateChangeEvent> {
                override fun onEvent(event: ConnectorStateChangeEvent) {
                    if (!fired && event.newState == tigase.halcyon.core.connector.State.Disconnected) {
                        connector.halcyon.eventBus.unregister(this)
                        fired = true
                        log.finest { "State changed. Calling handler." }
                        handler.invoke()
                    }
                }
            }
            try {
                connector.halcyon.eventBus.register(ConnectorStateChangeEvent, h)
                if (!fired && connector.state == tigase.halcyon.core.connector.State.Disconnected) {
                    connector.halcyon.eventBus.unregister(h)
                    fired = true
                    log.finest { "State is Disconnected already. Calling handler." }
                    handler.invoke()
                }
            } finally {
            }
        }
    }

    fun connect() {
        clear(Scope.Session)
        this.running = true
        log.info { "Connecting" }
        state = State.Connecting
        onConnecting()
        try {
            startConnector()
        } catch (e: Exception) {
            requestsManager.timeoutAll()
            state = State.Stopped
            throw e
        }
    }

    fun disconnect() {
        try {
            this.running = false
            log.info { "Disconnecting" }

            modules.getModuleOrNull(StreamManagementModule)?.let { module ->
                val ackEnabled =
                    module.resumptionContext.isAckActive
                if (ackEnabled && getConnectorState() == tigase.halcyon.core.connector.State.Connected) {
                    module.sendAck(true)
                }
            }

            state = State.Disconnecting
            onDisconnecting()
            stopConnector()
        } finally {
            clear(Scope.Session)
            requestsManager.timeoutAll()
            state = State.Stopped
        }
    }

    internal fun clear(scope: Scope) {
        internalDataStore.clear(scope)
        val scopes = Scope.values().filter { s -> s.ordinal <= scope.ordinal }.toTypedArray()
        eventBus.fire(ClearedEvent(scopes))
    }
}