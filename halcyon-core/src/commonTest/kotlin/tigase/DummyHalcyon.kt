package tigase

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.builder.ConfigurationBuilder
import tigase.halcyon.core.builder.createConfiguration
import tigase.halcyon.core.configuration.JIDPasswordSaslConfig
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SessionController
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.parser.parseXML
import tigase.halcyon.core.xmpp.FullJID
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import tigase.halcyon.core.xmpp.toBareJID

val dummyConfig = createConfiguration {
    auth {
        userJID = "user@example.com".toBareJID()
        password { "pencil" }
    }
    install(StreamManagementModule)
}

class DummyHalcyon(cf: ConfigurationBuilder = dummyConfig) : AbstractHalcyon(cf) {

    val sentElements = mutableListOf<Element>()

    inner class DummySessionController : SessionController {

        override val halcyon: AbstractHalcyon = this@DummyHalcyon

        override fun start() {
        }

        override fun stop() {
        }
    }

    inner class MockConnector : AbstractConnector(this) {

        override fun createSessionController(): SessionController = DummySessionController()

        override fun send(data: CharSequence) {
            try {
                val pr = parseXML(data.toString())
                pr.let {
                    sentElements.add(it)
                }
            } catch (ignore: Throwable) {
            }
        }

        override fun start() {
            state = tigase.halcyon.core.connector.State.Connected
        }

        override fun stop() {
            state = tigase.halcyon.core.connector.State.Disconnected
        }
    }

    override fun reconnect(immediately: Boolean) = throw NotImplementedError()

    override fun onConnecting() {
        boundJID = (config.sasl as JIDPasswordSaslConfig?)?.userJID?.let { FullJID(it, "1234") }
            ?: throw RuntimeException("No UserJID to bind!")
        requestsManager.boundJID = boundJID
    }

    override fun createConnector(): AbstractConnector = MockConnector()
    fun peekLastSend(): Element? = sentElements.removeLastOrNull()
    fun addReceived(stanza: Element) {
        eventBus.fire(ReceivedXMLElementEvent(stanza))
    }
}
