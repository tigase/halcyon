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

import tigase.halcyon.core.xml.*
import tigase.halcyon.core.xmpp.stanzas.Stanza

class Reference(val element: Element) : Element by element {

    var begin: Int? by intAttributeProperty()
    var end: Int? by intAttributeProperty()
    var type: String? by stringAttributeProperty()
    var uri: String? by stringAttributeProperty()
    var anchor: String? by stringAttributeProperty()
}

class File(val element: Element) : Element by element {

    var mediaType: String? by stringElementProperty("media-type")
    var fileName: String? by stringElementProperty("name")
    var fileSize: Int? by intElementProperty("size")
    var fileDescription: String? by stringElementProperty("desc")
}

fun Stanza<*>.getReferenceOrNull(): Reference? =
    getChildrenNS("reference", ReferenceModule.XMLNS)?.let { Reference(it) }

fun Reference.getMediaSharingFileOrNull(): File? =
    this.getChildrenNS("media-sharing", "urn:xmpp:sims:1")
        ?.getChildrenNS("file", "urn:xmpp:jingle:apps:file-transfer:5")
        ?.let { File(it) }

fun createFileSharingReference(
    uri: String,
    fileName: String?,
    mediaType: String,
    fileSize: Int?,
    fileDescription: String?
): Reference = Reference(
    element("reference") {
        xmlns = "urn:xmpp:reference:0"
        attributes["type"] = "data"
        attributes["uri"] = uri
        "media-sharing" {
            xmlns = "urn:xmpp:sims:1"
            "file" {
                xmlns = "urn:xmpp:jingle:apps:file-transfer:5"
                "media-type" { +mediaType }
                fileName?.let { "name" { +it } }
                fileDescription?.let { "desc" { +it } }
                fileSize?.let { "size" { +"$it" } }
            }
        }
    }
)
