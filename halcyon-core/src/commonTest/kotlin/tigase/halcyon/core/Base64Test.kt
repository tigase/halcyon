/*
 * Tigase Halcyon XMPP Library
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core

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

	@ExperimentalStdlibApi
	@Test
	fun encode() {
		assertEquals("", Base64.encode(""))
		assertEquals("Zg==", Base64.encode("f"))
		assertEquals("Zm8=", Base64.encode("fo"))
		assertEquals("Zm9v", Base64.encode("foo"))
		assertEquals("Zm9vYg==", Base64.encode("foob"))
		assertEquals("Zm9vYmE=", Base64.encode("fooba"))
		assertEquals("Zm9vYmFy", Base64.encode("foobar"))

		assertEquals("Zm9vYmFy", Base64.encode("foobar".encodeToByteArray()))

	}

}