package tigase.halcyon.rx

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.builder.createConfiguration
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.connector.SessionController
import tigase.halcyon.core.xmpp.toBareJID

class DummyHalcyon :
    AbstractHalcyon(
        createConfiguration {
            auth {
                userJID = "test@tester.com".toBareJID()
                passwordCallback = { "test" }
            }
        }
    ) {

    inner class DummySessionController : SessionController {

        override val halcyon: AbstractHalcyon
            get() = TODO("Not yet implemented")

        override fun start() {
        }

        override fun stop() {
        }
    }

    inner class DummyConnector : AbstractConnector(this) {

        override fun createSessionController(): SessionController = DummySessionController()

        override fun send(data: CharSequence) {
        }

        override fun start() {
            state = tigase.halcyon.core.connector.State.Connected
        }

        override fun stop() {
            state = tigase.halcyon.core.connector.State.Disconnected
        }
    }

    override fun reconnect(immediately: Boolean) {
        TODO("Not yet implemented")
    }

    override fun createConnector(): AbstractConnector = DummyConnector()
}
