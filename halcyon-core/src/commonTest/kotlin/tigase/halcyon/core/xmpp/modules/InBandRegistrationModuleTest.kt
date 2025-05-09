package tigase.halcyon.core.xmpp.modules

import kotlin.test.*
import tigase.DummyHalcyon
import tigase.halcyon.core.xmpp.forms.FieldType
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toBareJID
import tigase.requestResponse

class InBandRegistrationModuleTest {

    val halcyon = DummyHalcyon().apply {
        connect()
    }
    val module = halcyon.getModule<InBandRegistrationModule>(InBandRegistrationModule.TYPE)

    @Test
    fun test_registration_plain() {
        halcyon.requestResponse {
            request {
                it.getModule<InBandRegistrationModule>(InBandRegistrationModule.TYPE)
                    .requestRegistrationForm("example.com".toBareJID())
            }
            expectedRequest {
                iq {
                    "query" {
                        xmlns = "jabber:iq:register"
                    }
                }
            }
            response {
                iq {
                    type = IQType.Result
                    attributes["from"] = "example.com"
                    "query" {
                        xmlns = "jabber:iq:register"
                        "instructions" {
                            +"Choose a username and password for use with this service. Please also provide your email address."
                        }
                        "username" {}
                        "password" {}
                        "email" {}
                    }
                }
            }
            validate { it: Result<JabberDataForm>? ->
                assertNotNull(it).let {
                    it.onFailure {
                        fail()
                    }
                    it.onSuccess { form ->
                        assertNotNull(form.getFieldByVar("username")).let {
                            assertTrue(it.fieldRequired)
                            assertEquals(FieldType.TextSingle, it.fieldType)
                        }
                        assertNotNull(form.getFieldByVar("password")).let {
                            assertTrue(it.fieldRequired)
                            assertEquals(FieldType.TextPrivate, it.fieldType)
                        }
                        assertNotNull(form.getFieldByVar("email")).let {
                            assertTrue(it.fieldRequired)
                            assertEquals(FieldType.TextSingle, it.fieldType)
                        }
                    }
                }
            }
        }
    }
}
