package tigase.halcyon.tests

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.ReflectionModuleManager
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.StreamFeaturesEvent
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.auth.State
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.*

class SimpleConnectionTest {

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
		assertNotNull(halcyon.boundJID)

		var serverFeatues: List<String> = emptyList()
		halcyon.getModule<DiscoveryModule>()
			.info(halcyon.boundJID!!.domain.toJID())
			.response {
				it.onSuccess {
					serverFeatues = it.features
				}
			}
			.send()

		halcyon.waitForAllResponses()
		assertEquals(0, halcyon.requestsManager.getWaitingRequestsSize())
		assertTrue(serverFeatues.isNotEmpty())
		halcyon.disconnect()
		assertEquals(AbstractHalcyon.State.Stopped, halcyon.state, "Client should be connected to server.")
	}

}