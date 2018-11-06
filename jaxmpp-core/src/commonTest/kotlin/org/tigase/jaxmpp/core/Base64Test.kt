package org.tigase.jaxmpp.core

import kotlin.test.Test
import kotlin.test.assertEquals

class Base64Test {

	@Test
	fun decode() {
		assertEquals("", Base64.decodeToString(""))
		assertEquals("f", Base64.decodeToString("Zg=="))
		assertEquals("fo", Base64.decodeToString("Zm8="))
		assertEquals("foo", Base64.decodeToString("Zm9v"))
		assertEquals("foob", Base64.decodeToString("Zm9vYg=="))
		assertEquals("fooba", Base64.decodeToString("Zm9vYmE="))
		assertEquals("foobar", Base64.decodeToString("Zm9vYmFy"))
	}

	@Test
	fun encode() {
		assertEquals("", Base64.encode(""))
		assertEquals("Zg==", Base64.encode("f"))
		assertEquals("Zm8=", Base64.encode("fo"))
		assertEquals("Zm9v", Base64.encode("foo"))
		assertEquals("Zm9vYg==", Base64.encode("foob"))
		assertEquals("Zm9vYmE=", Base64.encode("fooba"))
		assertEquals("Zm9vYmFy", Base64.encode("foobar"))

	}

}