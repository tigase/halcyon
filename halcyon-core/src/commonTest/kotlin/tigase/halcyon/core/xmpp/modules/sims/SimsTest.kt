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
package tigase.halcyon.core.xmpp.modules.sims

import tigase.halcyon.core.xmpp.stanzas.MessageType
import tigase.halcyon.core.xmpp.stanzas.message
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SimsTest {

	val stanza = message {
		from = "alice@example.com/1".toJID()
		to = "bob@example.com/2".toJID()
		type = MessageType.Chat
		"body" {
			+"https://example.com/uploaded/b2718a48-ed88-4afe-99c2-ee99219d896f/first.jpg"
		}
		"reference" {
			xmlns = "urn:xmpp:reference:0"
			attributes["type"] = "data"
			attributes["uri"] = "https://example.com/uploaded/b2718a48-ed88-4afe-99c2-ee99219d896f/first.jpg"
			"media-sharing" {
				xmlns = "urn:xmpp:sims:1"
				"file" {
					xmlns = "urn:xmpp:jingle:apps:file-transfer:5"
					"media-type" { +"image/jpeg" }
					"name" { +"first.jpg" }
					"desc" { +"My photo" }
					"size" { +"3032449" }
				}
			}
		}
	}

	@Test
	fun getMetadataFromStanza() {
		assertNotNull(stanza.getReferenceOrNull()).let { ref ->
			assertEquals("data", ref.type)
			assertEquals("https://example.com/uploaded/b2718a48-ed88-4afe-99c2-ee99219d896f/first.jpg", ref.uri)
			assertEquals(ref.uri, stanza.body)

			assertNotNull(ref.getMediaSharingFileOrNull()).let { file ->
				assertEquals("image/jpeg", file.mediaType)
				assertEquals("first.jpg", file.fileName)
				assertEquals("My photo", file.fileDescription)
				assertEquals(3032449, file.fileSize)
			}

		}
	}

	@Test
	fun testCreateFileSharingReference() {
		val ref = createFileSharingReference(
			uri = "https://example.com/uploaded/b2718a48-ed88-4afe-99c2-ee99219d896f/first.jpg",
			fileName = "first.jpg",
			mediaType = "image/jpeg",
			fileSize = 3032449,
			fileDescription = "My photo"
		)
		assertEquals("data", ref.type)
		assertEquals("https://example.com/uploaded/b2718a48-ed88-4afe-99c2-ee99219d896f/first.jpg", ref.uri)

		assertNotNull(ref.getMediaSharingFileOrNull()).let { file ->
			assertEquals("image/jpeg", file.mediaType)
			assertEquals("first.jpg", file.fileName)
			assertEquals("My photo", file.fileDescription)
			assertEquals(3032449, file.fileSize)
		}
	}

}