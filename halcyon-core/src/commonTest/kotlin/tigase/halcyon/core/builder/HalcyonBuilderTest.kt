package tigase.halcyon.core.builder

import tigase.halcyon.core.configuration.domain
import tigase.halcyon.core.xmpp.toBareJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HalcyonBuilderTest {

	@Test
	fun simple_factory() {

		val halyon = createHalcyon {
			account {
				userJID = "a@localhost".toBareJID()
				password { "a" }
			}
		}
		assertNotNull(halyon.config.account).let {
			assertEquals("a@localhost".toBareJID(), it.userJID)
			assertEquals("a", it.passwordCallback.invoke())
		}
		assertNotNull(halyon.config).let {
			assertEquals("localhost", it.domain)
		}
	}

	@Test
	fun registration_factory() {

		val halyon = createHalcyon {
			createAccount {
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


		assertNull(halyon.config.account)
		assertNotNull(halyon.config.registration).let {
			assertEquals("localhost", it.domain)
			assertNotNull(it.formHandler)
		}

	}

}