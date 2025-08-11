package tigase.halcyon.core

import tigase.halcyon.core.builder.createConfiguration
import tigase.halcyon.core.connector.ConnectionErrorEvent
import tigase.halcyon.core.connector.socket.SocketConnectionErrorEvent
import tigase.halcyon.core.xmpp.modules.auth.SASLEvent
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import tigase.halcyon.core.xmpp.toBareJID
import java.lang.Thread.sleep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SocketConnectorTest {

//    private val config = """
//        handlers=java.util.logging.ConsoleHandler
//        .level=FINE
//        java.util.logging.ConsoleHandler.level=FINE
//    """.trimIndent()

    @Test
    fun testConnector() {
//        LogManager.getLogManager().readConfiguration(config.byteInputStream())

        val client = Halcyon(createConfiguration {
            auth {
                userJID = "testuser@tigase.org".toBareJID()
                password { "testuserpassword" }
            }
            install(StreamManagementModule)
        })

        var receivedSaslError: SASLModule.SASLError? = null
        
        client.eventBus.register<SASLEvent>(SASLEvent.TYPE) {
            if (it is SASLEvent.SASLError) {
                receivedSaslError = it.error
            }
        }
        client.eventBus.register<SocketConnectionErrorEvent>(ConnectionErrorEvent.TYPE) {
            if (it is SocketConnectionErrorEvent.HostNotFount) {
                Thread {
                    sleep(1000);
                    client.connect();
                }.start()
            }
        }
        client.connect()
        sleep(45*1000*1000)
        assertEquals(SASLModule.SASLError.NotAuthorized, receivedSaslError)
        println("connection timeout reached, checking state..")
        assertNotEquals(AbstractHalcyon.State.Connected, client.state)
    }
}
