package tigase.halcyon.tests

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.ReflectionModuleManager
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SentXMLElementEvent
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.exceptions.AuthenticationException
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.auth.State
import tigase.halcyon.core.xmpp.toBareJID
import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.*

class SimpleConnectionTest {

	init {
		val logger = Logger.getLogger("tigase")
		val handler: Handler = ConsoleHandler()
		handler.level = Level.ALL
		logger.addHandler(handler)
		logger.level = Level.ALL

	}

	@OptIn(ReflectionModuleManager::class)
	@Test
	fun simpleConnectionAndDisconnection() {
		val halcyon = createHalcyon()

		halcyon.eventBus.register<Event> {
			println("EVENT: $it")
		}

		halcyon.connectAndWait()
		println("Connected!")
		assertEquals(AbstractHalcyon.State.Connected, halcyon.state, "Client should be connected to server.")
		assertEquals(
			State.Success, halcyon.modules.getModule<SASLModule>().saslContext.state, "Client should be authenticated."
		)
		assertEquals("admin@sailboat.local".toBareJID(), assertNotNull(halcyon.boundJID).bareJID)


		halcyon.waitForAllResponses()
		assertEquals(0, halcyon.requestsManager.getWaitingRequestsSize())
		halcyon.disconnect()
		assertEquals(AbstractHalcyon.State.Stopped, halcyon.state, "Client should be connected to server.")
	}

	@Test
	fun notExistingUserLogin() {
		val halcyon = tigase.halcyon.core.builder.createHalcyon {
			auth {
				userJID = "oiujhgyuiklkjhb@sailboat.local".toBareJID()
				password { "dfghjsdnfgvsdfhjasdfasdfsdf" }
			}
		}
			.apply {
				eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) { println(">> ${it.element.getAsString()}") }
				eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) { println("<< ${it.element.getAsString()}") }
			}

		halcyon.eventBus.register<Event> {
			println("EVENT: $it")
		}

		assertFailsWith(AuthenticationException::class) {
			halcyon.connectAndWait()
		}
	}

}