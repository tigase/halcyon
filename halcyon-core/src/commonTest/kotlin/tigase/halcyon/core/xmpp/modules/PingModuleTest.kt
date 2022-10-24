package tigase.halcyon.core.xmpp.modules

import tigase.DummyHalcyon
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toJID
import tigase.requestResponse
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.fail

class PingModuleTest {

	val halcyon = DummyHalcyon().apply {
		connect()
	}

	@Test
	fun test_ping() {
		halcyon.requestResponse<PingModule.Pong> {
			request {
				it.getModule<PingModule>(PingModule.TYPE)
					.ping("entity@faraway.com".toJID())
			}
			expectedRequest {
				iq {
					type = IQType.Get
					to = "entity@faraway.com".toJID()
					"ping" {
						xmlns = "urn:xmpp:ping"
					}
				}
			}
			response {
				iq {
					from = "entity@faraway.com".toJID()
					to = "user@example.scom/1234".toJID()
					type = IQType.Result
				}
			}
			validate {
				assertNotNull(it).let {
					it.onFailure { fail(cause = it) }
					it.onSuccess { }
				}
			}
		}
	}

}