package tigase.halcyon.core.modules

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CriteriaTest {


	val element: Element = element("iq") {
		xmlns = "jabber:client"
		attribute("to", "a@b.c")
		attribute("from", "wojtas@wp.pl")
		attribute("type", "set")
		"pubsub" {
			xmlns = "a:b"
			"publish" {
				attribute("node", "123")
				"item" {
					attribute("id", "345")
					value = "x"
				}
				"item"{
					attribute("id", "456")
				}
				"item" {
					attribute("id", "567")
				}
			}
		}
	}

	@Test
	fun testCriterion() {
		assertFalse(
			tigase.halcyon.core.modules.Criterion.or(
				tigase.halcyon.core.modules.Criterion.name("X"),
				tigase.halcyon.core.modules.Criterion.name("Y")
			).match(element)
		)
		assertFalse(
			tigase.halcyon.core.modules.Criterion.or(
				tigase.halcyon.core.modules.Criterion.name("X"),
				tigase.halcyon.core.modules.Criterion.name("Y")
			).match(element)
		)
		assertTrue(
			tigase.halcyon.core.modules.Criterion.or(
				tigase.halcyon.core.modules.Criterion.name("X"),
				tigase.halcyon.core.modules.Criterion.name("iq")
			).match(element)
		)

		assertFalse(
			tigase.halcyon.core.modules.Criterion.and(
				tigase.halcyon.core.modules.Criterion.xmlns("jabber:client"),
				tigase.halcyon.core.modules.Criterion.name("X")
			).match(element)
		)
		assertTrue(
			tigase.halcyon.core.modules.Criterion.and(
				tigase.halcyon.core.modules.Criterion.xmlns("jabber:client"),
				tigase.halcyon.core.modules.Criterion.name("iq")
			).match(element)
		)

		assertTrue(
			tigase.halcyon.core.modules.Criterion.not(
				tigase.halcyon.core.modules.Criterion.and(
					tigase.halcyon.core.modules.Criterion.xmlns(
						"jabber:client"
					), tigase.halcyon.core.modules.Criterion.name("X")
				)
			).match(element)
		)
		assertFalse(
			tigase.halcyon.core.modules.Criterion.not(
				tigase.halcyon.core.modules.Criterion.and(
					tigase.halcyon.core.modules.Criterion.xmlns(
						"jabber:client"
					), tigase.halcyon.core.modules.Criterion.name("iq")
				)
			).match(element)
		)

		assertTrue(
			tigase.halcyon.core.modules.Criterion.chain(
				tigase.halcyon.core.modules.Criterion.name("iq"),
				tigase.halcyon.core.modules.Criterion.xmlns("a:b")
			).match(element)
		)
		assertFalse(
			tigase.halcyon.core.modules.Criterion.chain(
				tigase.halcyon.core.modules.Criterion.name("iq"),
				tigase.halcyon.core.modules.Criterion.xmlns("a:c")
			).match(element)
		)
		assertFalse(
			tigase.halcyon.core.modules.Criterion.chain(
				tigase.halcyon.core.modules.Criterion.name("presence"),
				tigase.halcyon.core.modules.Criterion.xmlns("a:b")
			).match(element)
		)
	}

}