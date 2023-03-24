package tigase.halcyon.core.builder

import tigase.halcyon.core.ReflectionModuleManager
import tigase.halcyon.core.configuration.JIDPasswordSaslConfig
import tigase.halcyon.core.configuration.declaredDomain
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.MessageModule
import tigase.halcyon.core.xmpp.modules.PingModule
import tigase.halcyon.core.xmpp.modules.auth.AnonymousSaslConfig
import tigase.halcyon.core.xmpp.modules.auth.SASL2Module
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.auth.authAnonymous
import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule
import tigase.halcyon.core.xmpp.modules.chatmarkers.ChatMarkersModule
import tigase.halcyon.core.xmpp.modules.chatmarkers.ChatMarkersModuleConfig
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.modules.mam.MAMModule
import tigase.halcyon.core.xmpp.modules.mix.MIXModule
import tigase.halcyon.core.xmpp.modules.presence.InMemoryPresenceStore
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.modules.roster.InMemoryRosterStore
import tigase.halcyon.core.xmpp.modules.roster.RosterModule
import tigase.halcyon.core.xmpp.toBareJID
import kotlin.test.*

class HalcyonBuilderTest {

	@Test
	fun simple_factory() {

		val halcyon = createHalcyon {
			auth {
				userJID = "a@localhost".toBareJID()
				password { "a" }
			}
			bind {
				resource = "test00909090"
			}
			presence {
				store = InMemoryPresenceStore()
			}
			roster {
				store = InMemoryRosterStore()
			}
			install(PingModule)
			install(ChatMarkersModule) {
				mode = ChatMarkersModuleConfig.Mode.All
			}
		}
		assertIs<JIDPasswordSaslConfig>(halcyon.config.sasl).let {
			assertEquals("a@localhost".toBareJID(), it.userJID)
			assertEquals("a", it.passwordCallback.invoke())
		}
		assertEquals("localhost", assertNotNull(halcyon.config).declaredDomain)
		assertEquals("test00909090", assertNotNull(halcyon.getModule(BindModule)).resource)
		assertEquals("https://tigase.org/halcyon", assertNotNull(halcyon.getModule(EntityCapabilitiesModule)).node)
	}

	@Test
	fun registration_factory() {

		val halyon = createHalcyon {
			register {
				domain = "localhost"
				registrationFormHandler { form ->
					form.getFieldByVar("username")!!.fieldValue = "user"
					form.getFieldByVar("password")!!.fieldValue = "password"
				}
				registrationHandler {
					it
				}
			}
		}


		assertNull(halyon.config.sasl)
		assertEquals("localhost", halyon.config.declaredDomain)
		assertNotNull(halyon.config.registration).let {
			assertEquals("localhost", it.domain)
			assertNotNull(it.formHandler)
		}

	}

	@Test
	fun anonymous_auth() {
		val cvg = createHalcyon {
			authAnonymous {
				domain = "example.com"
			}
		}
		assertEquals("example.com", cvg.config.declaredDomain)
		assertIs<AnonymousSaslConfig>(cvg.config.sasl)
	}

	@OptIn(ReflectionModuleManager::class)
	@Test
	fun modules_configuration() {
		val h = createHalcyon(false) {
			authAnonymous {
				domain = "example.com"
			}
			bind {
				resource = "blahblah"
			}
			install(PingModule)
			install(SASLModule) {
				enabled = false
			}
			install(SASL2Module)
			install(MIXModule)
		}


		assertNotNull(h.getModuleOrNull(DiscoveryModule))
		assertNotNull(h.getModuleOrNull(MIXModule))
		assertNotNull(h.getModuleOrNull(RosterModule))
		assertNotNull(h.getModuleOrNull(MAMModule))

		assertNull(h.getModuleOrNull(MessageModule))
		assertNotNull(h.getModuleOrNull(PingModule))
		assertNotNull(h.getModuleOrNull(PubSubModule))

		assertEquals("blahblah", assertNotNull(h.getModuleOrNull(BindModule)).resource)

		assertFalse(assertNotNull(h.getModuleOrNull(SASLModule)).enabled)
		assertTrue(assertNotNull(h.getModuleOrNull(SASL2Module)).enabled)

		assertEquals("blahblah", h.getModule<BindModule>().resource)
		assertFalse(h.getModule<SASLModule>().enabled)
		assertTrue(h.getModule<SASL2Module>().enabled)

		assertEquals("blahblah", h.getModule<BindModule>(BindModule.TYPE).resource)
		assertFalse(h.getModule<SASLModule>(SASLModule.TYPE).enabled)
		assertTrue(h.getModule<SASL2Module>(SASL2Module.TYPE).enabled)
	}

}