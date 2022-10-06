package tigase.halcyon.core.xmpp.modules.sm

import tigase.DummyHalcyon
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.toBareJID
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamManagementModuleTest {

	@Test
	fun testProcessAndEvents() {
		val halcyon = DummyHalcyon().also {
			it.configure {
				userJID = "test@tester.com".toBareJID()
				password = "test"
				resource = "test"
			}
			it.connect()
		}

		val smm: StreamManagementModule = halcyon.modules[StreamManagementModule.TYPE]
		smm.resumptionContext.isAckEnabled = true
		smm.resumptionContext.isActive= true

		assertEquals(0, smm.resumptionContext.incomingH)
		assertEquals(0, smm.resumptionContext.outgoingH)
		halcyon.writeDirectly(element("x") {})
		halcyon.writeDirectly(element("iq") {})
		assertEquals(1, smm.resumptionContext.outgoingH)
		assertEquals(0, smm.resumptionContext.incomingH)
		halcyon.writeDirectly(element("iq") {})
		halcyon.writeDirectly(element("presence") {})
		halcyon.writeDirectly(element("presence") {})
		halcyon.writeDirectly(element("presence") {})
		halcyon.writeDirectly(element("presence") {})
		halcyon.writeDirectly(element("message") {})
		halcyon.writeDirectly(element("starttls") {xmlns="urn:ietf:params:xml:ns:xmpp-tls"})
		halcyon.writeDirectly(element("auth") {xmlns = SASLModule.XMLNS})
		halcyon.writeDirectly(element("stream:features") {})
		halcyon.writeDirectly(element("features") {xmlns="http://etherx.jabber.org/streams"})
		halcyon.writeDirectly(element("a") {})
		assertEquals(0, smm.resumptionContext.incomingH)
		assertEquals(7, smm.resumptionContext.outgoingH)

		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("r") {xmlns = StreamManagementModule.XMLNS}))
		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("a") {xmlns = StreamManagementModule.XMLNS}))
		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("challenge") {xmlns = SASLModule.XMLNS}))
		assertEquals(0, smm.resumptionContext.incomingH)
		assertEquals(7, smm.resumptionContext.outgoingH)

		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("iq") {}))
		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("presence") {}))
		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("presence") {}))
		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("presence") {}))
		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("message") {}))
		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("nothing") {}))
		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("stream:features") {}))
		halcyon.eventBus.fire(ReceivedXMLElementEvent(element("features") {xmlns="http://etherx.jabber.org/streams"}))
		assertEquals(5, smm.resumptionContext.incomingH)
		assertEquals(8, smm.resumptionContext.outgoingH)

	}

}