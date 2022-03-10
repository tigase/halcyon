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
//package tigase.halcyon.core.connector.socket
//
//import kotlinx.cinterop.*
//import platform.Network.*
//import platform.darwin.*
//import platform.posix.size_t
//import platform.posix.usleep
//import tigase.halcyon.core.Halcyon
//import tigase.halcyon.core.connector.*
//import tigase.halcyon.core.excutor.TickExecutor
//import tigase.halcyon.core.logger.Level
//import tigase.halcyon.core.logger.LoggerFactory
//import tigase.halcyon.core.xml.Element
//import tigase.halcyon.core.xml.element
//import tigase.halcyon.core.xml.parser.StreamParser
//import tigase.halcyon.core.xmpp.ErrorCondition
//import tigase.halcyon.core.xmpp.XMPPException
//import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
//import kotlin.time.Duration.Companion.seconds
//
//class SocketConnectorNetwork(halcyon: Halcyon) : AbstractConnector(halcyon) {
//
//    companion object {
//
//        const val SERVER_HOST = "tigase.halcyon.core.connector.socket.SocketConnector#serverHost"
//        const val SERVER_PORT = "tigase.halcyon.core.connector.socket.SocketConnector#serverPort"
//        const val SEE_OTHER_HOST_KEY = "tigase.halcyon.core.connector.socket.SocketConnector#seeOtherHost"
//
//        const val XMLNS_START_TLS = "urn:ietf:params:xml:ns:xmpp-tls"
//    }
//
//    var secured: Boolean = false
//        private set
//
//    private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.SocketConnector")
//    private var config: SocketConnectorConfig = halcyon.config.connectorConfig as SocketConnectorConfig
//    private var resolver: DnsResolver = DnsResolver();
//    private val whitespacePingExecutor = TickExecutor(halcyon.eventBus, 30.seconds) { onTick() }
//    private val parser = object : StreamParser() {
//
//        private fun logReceivedStanza(element: Element) {
//            when {
//                log.isLoggable(Level.FINEST) -> log.finest("Received element ${element.getAsString()}")
//                log.isLoggable(Level.FINER) -> log.finer("Received element ${
//                    element.getAsString(deep = 3, showValue = false)
//                }")
//                log.isLoggable(Level.FINE) -> log.fine("Received element ${
//                    element.getAsString(deep = 2, showValue = false)
//                }")
//            }
//        }
//
//        override fun onNextElement(element: Element) {
//            logReceivedStanza(element)
//            processReceivedElement(element)
//        }
//
//        override fun onStreamClosed() {
//            log.finest { "Stream closed" }
//            halcyon.eventBus.fire(StreamTerminatedEvent())
//        }
//
//        override fun onStreamStarted(attrs: Map<String, String>) {
//            log.finest { "Stream started: $attrs" }
//            halcyon.eventBus.fire(StreamStartedEvent(attrs))
//        }
//
//        override fun onParseError(errorMessage: String) {
//            log.finest { "Parse error: $errorMessage" }
//            halcyon.eventBus.fire(ParseErrorEvent(errorMessage))
//        }
//    }
//
//    private var socket: nw_connection_t = null;
//    private var sslEngine: SSLEngine? = null;
//    private val queue = dispatch_queue_create("SocketConnector_Network", null);
//
//    override fun createSessionController(): SessionController {
//        return SocketSessionController(halcyon, this);
//    }
//
//    override fun send(data: CharSequence) {
//        val bytes = StringBuilder().append(data).toString().encodeToByteArray();
//        sslEngine?.let { sslEngine ->
//            sslEngine?.encrypt(bytes);
//        } ?: run {
//            writeDataToSocket(bytes);
//        }
//    }
//
//    fun restartStream() {
//        val userJid = halcyon.config.userJID!!
//
//        val sb = buildString {
//            append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ")
//            append("version='1.0' ")
//            append("from='$userJid' ")
//            append("to='${userJid.domain}'>")
//        }
//        send(sb)
//    }
//
//    fun startTLS() {
//        log.info { "Running StartTLS" }
//        val element = element("starttls") {
//            xmlns = XMLNS_START_TLS
//        }
//        halcyon.writer.writeDirectly(element)
//    }
//
//    override fun start() {
//        //this.ensureNeverFrozen();
//        state = State.Connecting
//
//        val userJid = halcyon.config.userJID!!
//
//        createSocket() { newSocket ->
//            this.socket = newSocket;
//            log.fine { "Opening socket connection" }
//
//            nw_connection_set_queue(socket, queue);
//            nw_connection_set_state_changed_handler(socket) { state: nw_connection_state_t, error: nw_error_t? ->
//                // we need to handle this somehow..
//                error?.let { err ->
//                    log.fine("Error: ${err}")
//                }
//                log.finest { "Connection state changed to ${state}" }
//                when (state) {
//                    nw_connection_state_ready -> {
//                        log.fine { "Connection established" }
//                        this.scheduleRead();
//
//                        restartStream();
//
//                        this.whitespacePingExecutor.start()
//                    }
//                    nw_connection_state_preparing -> {
//                    }
//                    nw_connection_state_cancelled -> this.state = State.Disconnected;
//                    nw_connection_state_failed -> connectionError(SocketConnectionErrorEvent.Timeout())
//                    nw_connection_state_waiting -> connectionError(SocketConnectionErrorEvent.HostNotFount())
//                    else -> {}
//                }
//                log.finest { "Connection state change processed" }
//            }
//            nw_connection_set_viability_changed_handler(socket) { viable ->
//                log.finest { "Viability changed: ${viable}"}
//            }
//
//            dispatch_async(queue) {
//                log.finest { "Starting connection.." }
//                nw_connection_start(socket);
//            }
//        }
//    }
//
//    override fun stop() {
//        if ((state != State.Disconnected)) {
//            log.fine { "Stopping..." }
//            try {
//                if (state == State.Connected) closeStream()
//                state = State.Disconnecting
//                whitespacePingExecutor.stop()
//                usleep(175000)
//                if (state != State.Disconnected) {
//                    nw_connection_cancel(socket);
//                }
//            } finally {
//                state = State.Disconnected
//                eventsEnabled = false
//            }
//        }
//    }
//
//    private fun closeStream() {
//        send("</stream:stream>");
//    }
//
//    private fun connectionError(cause: SocketConnectionErrorEvent) {
//        halcyon.eventBus.fire(cause);
//        state = when (state) {
//            State.Connecting -> State.Disconnected
//            State.Connected -> State.Disconnecting
//            State.Disconnecting -> State.Disconnected
//            State.Disconnected -> State.Disconnected
//        }
//        if (state == State.Disconnected) eventsEnabled = false
//    }
//
//    private fun scheduleRead() {
//        nw_connection_receive(socket, 1, 4096) { data: dispatch_data_t?, context: nw_content_context_t?, complete: Boolean, error: nw_error_t? ->
//            data?.let {
//                this.process(it);
//                this.scheduleRead();
//            }
//        };
//    }
//
//    private fun process(data: dispatch_data_t?) {
//        var output = ByteArray(0);
//        dispatch_data_apply(data) { region: dispatch_data_t?, offset: size_t, buffer: COpaquePointer?, size: size_t ->
//            output += buffer!!.reinterpret<UTF8CharVar>().readBytes(size.toInt());
//            true;
//        }
//        sslEngine?.let { sslEngine ->
//            sslEngine?.decrypt(output);
//        } ?: run {
//            process(output);
//        }
//    }
//
//    private fun processReceivedElement(element: Element) {
//        when (element.xmlns) {
//            // FIXME: NOT IMPLEMENTED YET!!
//            XMLNS_START_TLS -> processTLSStanza(element)
//            else -> halcyon.eventBus.fire(ReceivedXMLElementEvent(element))
//        }
//    }
//
//    private fun processTLSStanza(element: Element) {
//        when (element.name) {
//            "proceed" -> {
//                proceedTLS()
//            }
//            "failure" -> {
//                log.warning { "Cannot establish TLS connection!" }
//                halcyon.eventBus.fire(SocketConnectionErrorEvent.TLSFailureEvent())
//            }
//            else -> throw XMPPException(ErrorCondition.BadRequest)
//        }
//    }
//
//    private fun proceedTLS() {
//        log.info { "Proceeding TLS" }
//        sslEngine = SSLEngine(this, halcyon.config.userJID!!.domain);
//        restartStream();
//    }
//
//    private fun createSocket(name: String, port: Int): nw_connection_t {
//        val configure_tls = NW_PARAMETERS_DISABLE_PROTOCOL;
//        log.finest { "Creating connection socket to '${name}:${port}'"}
//        val endpoint = nw_endpoint_create_host(name, "" + port);
//        val params = nw_parameters_create_secure_tcp(configure_tls) { options ->
//            nw_tcp_options_set_no_delay(options, true);
//            nw_tcp_options_set_connection_timeout(options, 1);
//        }
//        nw_parameters_set_service_class(params, nw_service_class_responsive_data);
//        return nw_connection_create(endpoint, params);
//    }
//
//    private fun createSocket(completionHandler: (nw_connection_t)->Unit) {
//        val location = halcyon.getModule<StreamManagementModule>(StreamManagementModule.TYPE).resumptionContext.location
//        if (location != null) {
//            return completionHandler(createSocket(location, config.port));
//        }
//
//        val seeOther = halcyon.internalDataStore.getData<String>(SEE_OTHER_HOST_KEY)
//        if (seeOther != null) {
//            return completionHandler(createSocket(seeOther, config.port));
//        }
//
////		val forcedHost = halcyon.sessionObject.getProperty<String>(SERVER_HOST)
////		if (forcedHost != null) {
////			return Socket(InetAddress.getByName(forcedHost), config.port)
////		}
//
//        val userJid = halcyon.config.userJID!!
//        resolver = DnsResolver();
//        resolver.resolve(userJid.domain) { result ->
//            result.onSuccess { records ->
//                completionHandler(createSocket(records.first().target, records.first().port.toInt()))
//            }.onFailure {
//                completionHandler(createSocket(userJid.domain, config.port));
//            }
//        }
//    }
//
//    private fun onTick() {
//        // FIXME: Do we need whitespace ping??
////        if (state == State.Connected && whiteSpaceEnabled) {
////            log.finer { "Whitespace ping" }
////            log.finer { "Whitespace ping" }
////            worker.writer.write(' '.code)
////            worker.writer.flush()
////        }
//    }
//
//    fun process(data: ByteArray) {
//        parser.parse(data.toKString());
//    }
//
//    fun writeDataToSocket(data: ByteArray) {
//        println("1")
//        val out = dispatch_data_create(data.toCValues(), data.size.toULong(), null,
//            null);
//        println("2")
//        val completion: nw_connection_send_completion_t =  { error ->
//            if (error != null) {
//                this.connectionError(SocketConnectionErrorEvent.Unknown(error));
//            }
//        }
//        nw_connection_send(socket, out, NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT, true, completion);
//        println("3")
////        { error ->
////            if (error != null) {
////                this.connectionError(SocketConnectionErrorEvent.Unknown(error));
////            }
////        }
//    }
//}