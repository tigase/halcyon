package org.tigase.jaxmpp.core.modules

import org.tigase.jaxmpp.core.xml.Element
import org.tigase.jaxmpp.core.xml.stanza
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CriteriaTest {

	val element: Element = stanza("iq") {
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
		assertFalse(Criterion.or(Criterion.name("X"), Criterion.name("Y")).match(element))
		assertFalse(Criterion.or(Criterion.name("X"), Criterion.name("Y")).match(element))
		assertTrue(Criterion.or(Criterion.name("X"), Criterion.name("iq")).match(element))

		assertFalse(Criterion.and(Criterion.xmlns("jabber:client"), Criterion.name("X")).match(element))
		assertTrue(Criterion.and(Criterion.xmlns("jabber:client"), Criterion.name("iq")).match(element))

		assertTrue(Criterion.not(Criterion.and(Criterion.xmlns("jabber:client"), Criterion.name("X"))).match(element))
		assertFalse(Criterion.not(Criterion.and(Criterion.xmlns("jabber:client"), Criterion.name("iq"))).match(element))
	}

}