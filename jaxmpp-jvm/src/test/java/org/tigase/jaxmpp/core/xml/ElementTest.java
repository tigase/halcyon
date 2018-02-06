package org.tigase.jaxmpp.core.xml;

import org.junit.Test;

import static junit.framework.TestCase.*;

public class ElementTest {

	private static Element createElement() {
		ElementBuilder b = ElementBuilder.Companion.create("message")
				.attribute("to", "romeo@example.net")
				.attribute("from", "juliet@example.com/balcony")
				.attribute("type", "chat")
				.child("subject")
				.value("I implore you!")
				.up()
				.child("body")
				.value("Wherefore art thou, Romeo?")
				.up()
				.child("thread")
				.value("e0ffe42b28561960c6b12b944a092794b9683a38")
				.up()
				.child("x")
				.value("tigase:offline")
				.xmlns("tigase");
		return b.getElement();
	}

	@Test
	public void testFindChild() {
		final Element element = createElement();

		Element nullElement = element.findChild("message", "missing");
		assertNull(nullElement);

		nullElement = element.findChild("x", "body");
		assertNull(nullElement);

		Element c = element.findChild("message", "body");
		assertNotNull(c);
		assertEquals("body", c.getName());
		assertEquals("Wherefore art thou, Romeo?", c.getValue());

		System.out.println(element.getAsString());
	}

	@Test
	public void testNextSibling() {
		final Element element = createElement();
		Element c = element.findChild("message", "body");
		assertNotNull(c);
		assertEquals("body", c.getName());
		assertEquals("thread", c.getNextSibling().getName());
	}
}
