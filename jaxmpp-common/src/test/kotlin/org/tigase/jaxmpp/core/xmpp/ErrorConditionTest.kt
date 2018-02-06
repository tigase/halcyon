package org.tigase.jaxmpp.core.xmpp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ErrorConditionTest {

	@Test
	fun testElementByName() {
		assertEquals(ErrorCondition.feature_not_implemented,
					 ErrorCondition.Companion.getByElementName("feature-not-implemented"))
		assertNotEquals(ErrorCondition.conflict, ErrorCondition.Companion.getByElementName("feature-not-implemented"))
		assertEquals(ErrorCondition.Unknown, ErrorCondition.Companion.getByElementName("###"))

		assertEquals("feature-not-implemented", ErrorCondition.feature_not_implemented.elementName)
	}

}