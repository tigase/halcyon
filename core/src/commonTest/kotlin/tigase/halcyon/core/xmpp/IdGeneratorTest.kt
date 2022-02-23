/*
 * halcyon-core
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
package tigase.halcyon.core.xmpp

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdGeneratorTest {

	@Test
	fun testUIDGenerator() {
		val g = IdGenerator()
		val v1 = g.nextId()
		val v2 = g.nextId()
		assertTrue(v1.isNotEmpty())
		assertTrue(v1.isNotBlank())
		assertTrue(v2.isNotEmpty())
		assertTrue(v2.isNotBlank())
		assertNotEquals(v1, v2)
	}
}