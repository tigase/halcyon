package tigase.halcyon.core.xmpp.modules.sm

import kotlin.test.Test
import kotlin.test.assertEquals
import tigase.DummyHalcyon
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.modules.auth.SASLModule

class StreamManagementModuleTest {

    @Test
    fun testProcessAndEvents() {
        val halcyon = DummyHalcyon().also {
            it.connect()
        }

        val smm: StreamManagementModule = halcyon.modules[StreamManagementModule.TYPE]
        smm.withResumptionContext { ctx ->
            ctx.state = StreamManagementModule.State.active
        }

        smm.withResumptionContext { ctx ->
            assertEquals(0, ctx.incomingH)
            assertEquals(0, ctx.outgoingH)
        }
        halcyon.writeDirectly(element("x") {})
        halcyon.writeDirectly(element("iq") {})
        smm.withResumptionContext { ctx ->
            assertEquals(1, ctx.outgoingH)
            assertEquals(0, ctx.incomingH)
        }
        halcyon.writeDirectly(element("iq") {})
        halcyon.writeDirectly(element("presence") {})
        halcyon.writeDirectly(element("presence") {})
        halcyon.writeDirectly(element("presence") {})
        halcyon.writeDirectly(element("presence") {})
        halcyon.writeDirectly(element("message") {})
        halcyon.writeDirectly(element("starttls") { xmlns = "urn:ietf:params:xml:ns:xmpp-tls" })
        halcyon.writeDirectly(element("auth") { xmlns = SASLModule.XMLNS })
        halcyon.writeDirectly(element("stream:features") {})
        halcyon.writeDirectly(element("features") { xmlns = "http://etherx.jabber.org/streams" })
        halcyon.writeDirectly(element("a") {})
        smm.withResumptionContext { ctx ->
            assertEquals(0, ctx.incomingH)
            assertEquals(7, ctx.outgoingH)
        }

        smm.processElementReceived(element("r") { xmlns = StreamManagementModule.XMLNS })
        smm.processElementReceived(element("a") { xmlns = StreamManagementModule.XMLNS })
        smm.processElementReceived(element("challenge") { xmlns = SASLModule.XMLNS })
        smm.withResumptionContext { ctx ->
            assertEquals(0, ctx.incomingH)
            assertEquals(7, ctx.outgoingH)
        }

        smm.processElementReceived(element("iq") {})
        smm.processElementReceived(element("presence") {})
        smm.processElementReceived(element("presence") {})
        smm.processElementReceived(element("presence") {})
        smm.processElementReceived(element("message") {})
        smm.processElementReceived(element("nothing") {})
        smm.processElementReceived(element("stream:features") {})
        smm.processElementReceived(
            element("features") {
                xmlns = "http://etherx.jabber.org/streams"
            }
        )
        smm.withResumptionContext { ctx ->
            assertEquals(5, ctx.incomingH)
            assertEquals(7, ctx.outgoingH)
        }
    }
}
