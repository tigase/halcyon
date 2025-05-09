package tigase.halcyon.coroutines

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlinx.coroutines.*
import tigase.halcyon.core.requests.XMPPError
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.stanzas.IQType

@ExperimentalCoroutinesApi
@DelicateCoroutinesApi
class CoroutineRequestTest {

    val halcyon = DummyHalcyon().also {
        it.connect()
    }

    @kotlin.test.Test
    fun testRequestSuccess() {
        val rm = halcyon.requestsManager

        val e = element("iq") {
            attribute("id", "1")
            attribute("type", "get")
            attribute("to", "a@b.c")
        }

        var successCounter = 0

        GlobalScope.launch(Dispatchers.Unconfined) {
            halcyon.request.iq(e)
                .awaitResponse()
            ++successCounter
        }

        val resp = element("iq") {
            attribute("id", "1")
            attribute("type", "result")
            attribute("from", "a@b.c")
        }
        val handler = rm.getRequest(resp)

        assertNotNull(
            handler,
            "Request was not registered in RequestsManager"
        ).setResponseStanza(resp)
        assertEquals(1, successCounter)
    }

    @kotlin.test.Test
    fun testRequestPremappedSuccess() {
        var successCounter = 0

        GlobalScope.launch(Dispatchers.Unconfined) {
            val res = halcyon.request.iq {
                attribute("id", "1213")
                attribute("type", "get")
                attribute("to", "a@b.c")
            }
                .map {
                    it.attributes["id"]!!
                }
                .awaitResponse()
            if (res == "1213") ++successCounter
        }

        val resp = element("iq") {
            attribute("id", "1213")
            attribute("type", "result")
            attribute("from", "a@b.c")
        }
        assertNotNull(
            halcyon.requestsManager.getRequest(resp),
            "Request was not registered in RequestsManager"
        ).setResponseStanza(resp)
        assertEquals(1, successCounter)
    }

    @kotlin.test.Test
    fun testRequestError() {
        var failureCounter = 0

        GlobalScope.launch(Dispatchers.Unconfined) {
            try {
                halcyon.request.iq {
                    attribute("id", "1213")
                    attribute("type", "get")
                    attribute("to", "a@b.c")
                }
                    .awaitResponse()
            } catch (e: XMPPError) {
                assertEquals(ErrorCondition.NotAllowed, e.error)
                ++failureCounter
            } catch (e: Throwable) {
                fail("It should not happend", e)
            }
        }

        val resp = element("iq") {
            attribute("id", "1213")
            attribute("type", "error")
            attribute("from", "a@b.c")
            element("error") {
                attribute("type", "cancel")
                element("not-allowed") {
                    xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
                }
            }
        }
        assertNotNull(
            halcyon.requestsManager.getRequest(resp),
            "Request was not registered in RequestsManager"
        ).setResponseStanza(resp)
        assertEquals(1, failureCounter)
    }

    @kotlin.test.Test
    fun testRequestErrorNotAcceptableResponseMapInternal() {
        var failureCounter = 0
        GlobalScope.launch(Dispatchers.Unconfined) {
            try {
                halcyon.request.iq {
                    attribute("id", "1213")
                    attribute("type", "get")
                    attribute("to", "a@b.c")
                }
                    .map {
                        if (it.type ==
                            IQType.Result
                        ) {
                            throw XMPPError(it, ErrorCondition.NotAcceptable, null)
                        }
                        it
                    }
                    .awaitResponse()
            } catch (e: XMPPError) {
                assertEquals(ErrorCondition.NotAcceptable, e.error)
                ++failureCounter
            } catch (e: Throwable) {
                fail("It should not happend", e)
            }
        }
        val resp = element("iq") {
            attribute("id", "1213")
            attribute("type", "result")
            attribute("from", "a@b.c")
        }
        assertNotNull(
            halcyon.requestsManager.getRequest(resp),
            "Request was not registered in RequestsManager"
        ).setResponseStanza(resp)
        assertEquals(1, failureCounter)
    }
}
