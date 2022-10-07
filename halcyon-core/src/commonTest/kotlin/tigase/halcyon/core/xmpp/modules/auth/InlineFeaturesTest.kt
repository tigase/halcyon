package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.xml.element
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InlineFeaturesTest {

	val featuresItem = element("authentication") {
		xmlns = "urn:xmpp:sasl:2"
		"mechanism" { +"SCRAM-SHA-256" }
		"inline" {
			"sm" { xmlns = "urn:xmpp:sm:3" }
			"bind" {
				xmlns = "urn:xmpp:bind:0"
				"inline" {
					"feature" { attributes["var"] = "urn:xmpp:carbons:2" }
					"feature" { attributes["var"] = "urn:xmpp:sm:3" }
				}
			}
		}
	}

	@Test
	fun testSupports() {
		val inlineFeatures = InlineFeatures.create(featuresItem)
		assertTrue(inlineFeatures.supports("sm", "urn:xmpp:sm:3"))
		assertTrue(inlineFeatures.supports("bind", "urn:xmpp:bind:0"))
		assertFalse(inlineFeatures.supports("unknown", "not:exists"))
		assertFalse(inlineFeatures.supports("not:exists:2"))
	}

	@Test
	fun testSupportsFeatures() {
		val inlineFeatures = InlineFeatures.create(featuresItem)
			.subInline("bind", "urn:xmpp:bind:0")
		assertFalse(inlineFeatures.supports("sm", "urn:xmpp:sm:3"))
		assertFalse(inlineFeatures.supports("bind", "urn:xmpp:bind:0"))
		assertFalse(inlineFeatures.supports("unknown", "not:exists"))
		assertTrue(inlineFeatures.supports("urn:xmpp:carbons:2"))
		assertTrue(inlineFeatures.supports("urn:xmpp:sm:3"))
		assertFalse(inlineFeatures.supports("not:exists"))
	}

}