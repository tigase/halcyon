package tigase.halcyon.core.xmpp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ErrorConditionTest {

	@Test
	fun testElementByName() {
		assertEquals(ErrorCondition.FeatureNotImplemented,
					 ErrorCondition.Companion.getByElementName("feature-not-implemented"))
		assertNotEquals(ErrorCondition.Conflict, ErrorCondition.Companion.getByElementName("feature-not-implemented"))
		assertEquals(ErrorCondition.Unknown, ErrorCondition.Companion.getByElementName("###"))

		assertEquals("feature-not-implemented", ErrorCondition.FeatureNotImplemented.elementName)
	}

}