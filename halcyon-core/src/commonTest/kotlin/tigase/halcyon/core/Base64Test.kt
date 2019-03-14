package tigase.halcyon.core

import kotlin.test.Test
import kotlin.test.assertEquals

class Base64Test {

	@Test
	fun decode() {
		assertEquals("", tigase.halcyon.core.Base64.decodeToString(""))
		assertEquals("f", tigase.halcyon.core.Base64.decodeToString("Zg=="))
		assertEquals("fo", tigase.halcyon.core.Base64.decodeToString("Zm8="))
		assertEquals("foo", tigase.halcyon.core.Base64.decodeToString("Zm9v"))
		assertEquals("foob", tigase.halcyon.core.Base64.decodeToString("Zm9vYg=="))
		assertEquals("fooba", tigase.halcyon.core.Base64.decodeToString("Zm9vYmE="))
		assertEquals("foobar", tigase.halcyon.core.Base64.decodeToString("Zm9vYmFy"))
	}

	@Test
	fun encode() {
		assertEquals("", tigase.halcyon.core.Base64.encode(""))
		assertEquals("Zg==", tigase.halcyon.core.Base64.encode("f"))
		assertEquals("Zm8=", tigase.halcyon.core.Base64.encode("fo"))
		assertEquals("Zm9v", tigase.halcyon.core.Base64.encode("foo"))
		assertEquals("Zm9vYg==", tigase.halcyon.core.Base64.encode("foob"))
		assertEquals("Zm9vYmE=", tigase.halcyon.core.Base64.encode("fooba"))
		assertEquals("Zm9vYmFy", tigase.halcyon.core.Base64.encode("foobar"))

	}

}