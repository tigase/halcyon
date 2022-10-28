package tigase.halcyon.core.builder

import tigase.halcyon.core.ReflectionModuleManager
import tigase.halcyon.core.configuration.JIDPasswordSaslConfig
import tigase.halcyon.core.configuration.domain
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.MessageModule
import tigase.halcyon.core.xmpp.modules.PingModule
import tigase.halcyon.core.xmpp.modules.auth.AnonymousSaslConfig
import tigase.halcyon.core.xmpp.modules.auth.SASL2Module
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.auth.authAnonymous
import tigase.halcyon.core.xmpp.toBareJID
import kotlin.test.*

class HalcyonBuilderTest {

	@Test
	fun simple_factory() {

		val halyon = createHalcyon {
			auth {
				userJID = "a@localhost".toBareJID()
				password { "a" }
			}
		}
		assertIs<JIDPasswordSaslConfig>(halyon.config.sasl).let {
			assertEquals("a@localhost".toBareJID(), it.userJID)
			assertEquals("a", it.passwordCallback.invoke())
		}
		assertEquals("localhost", assertNotNull(halyon.config).domain)
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
		assertEquals("localhost", halyon.config.domain)
		assertNotNull(halyon.config.registration).let {
			assertEquals("localhost", it.domain)
			assertNotNull(it.formHandler)
		}

	}

	@Test
	fun anonymous_auth() {
		val cvg = createConfiguration {
			authAnonymous {
				domain = "example.com"
			}
		}
		assertEquals("example.com", cvg.domain)
		assertIs<AnonymousSaslConfig>(cvg.sasl)
	}

	@OptIn(ReflectionModuleManager::class)
	@Test
	fun modules_configuration() {
		val cvg = createHalcyon {
			authAnonymous {
				domain = "example.com"
			}
			modules {
				install(PingModule)
				install(BindModule) {
					resource = "blahblah"
				}
				install(SASLModule) {
					enabled = false
				}
			}
			modules {
				install(SASL2Module)
			}
		}

		assertNull(cvg.getModuleOrNull(MessageModule))
		assertNotNull(cvg.getModuleOrNull(PingModule))
		assertNotNull(cvg.getModuleOrNull(BindModule))

		assertNotNull(cvg.getModuleOrNull(SASLModule)).let {
			assertFalse(it.enabled)
		}
		assertNotNull(cvg.getModuleOrNull(SASL2Module)).let {
			assertTrue(it.enabled)
		}

		assertEquals("blahblah", cvg.getModule<BindModule>().resource)
		assertFalse(cvg.getModule<SASLModule>().enabled)
		assertTrue(cvg.getModule<SASL2Module>().enabled)
	}

}