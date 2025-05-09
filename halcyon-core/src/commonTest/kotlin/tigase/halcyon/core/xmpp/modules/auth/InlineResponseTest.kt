package tigase.halcyon.core.xmpp.modules.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import tigase.halcyon.core.xml.element

class InlineResponseTest {

    val response = InlineResponse(
        InlineProtocolStage.AfterSasl,
        element("success") {
            xmlns = "urn:xmpp:sasl:2"
            "additional-data" { +"123" }
            "authorization-identifier" { +"juliet@montague.example" }
            "resumed" {
                xmlns = "urn:xmpp:sm:3"
                attributes["h"] = "345"
                attributes["previd"] = "124"
            }
        }
    )

    @Test
    fun testWhenExists() {
        var found = false
        response.whenExists(InlineProtocolStage.AfterSasl, "resumed", "urn:xmpp:sm:3") {
            found = true
            assertEquals("124", it.attributes["previd"])
        }
        assertTrue(found)
        response.whenExists(InlineProtocolStage.AfterSasl, "nothing", "not:existed:0") {
            fail()
        }
        response.whenExists(InlineProtocolStage.AfterBind, "resumed", "urn:xmpp:sm:3") { fail() }
    }

    @Test
    fun testWhenNotExists() {
        response.whenNotExists(InlineProtocolStage.AfterSasl, "resumed", "urn:xmpp:sm:3") {
            fail()
        }

        var notFound = false
        response.whenNotExists(InlineProtocolStage.AfterSasl, "nothing", "not:existed:0") {
            notFound = true
        }
        assertTrue(notFound)

        response.whenNotExists(InlineProtocolStage.AfterBind, "nothing", "not:existed:0") {
            fail()
        }
    }

// 	@Test
// 	fun testWhenElse() {
// 		var found = false
// 		response.whenExists(InlineProtocolStage.AfterSasl, "nothing", "not:existed:0") {
// 			fail()
// 		} ifNotExists { found = true }
// 		assertTrue(found)
// 	}
}
