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
package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.toJID

/**
 * Representation of a reply to metadata (Message Replies, see https://xmpp.org/extensions/xep-0461.html)
 */
data class ReplyTo(
    /**
     * Sender of message to which this is a reply to.
     */
    val senderJID: JID,

    /**
     * ID of the message to which this is a reply to (`origin-id` or message "id" for 1-1 and `stable-id` for groupchat messages).
     */
    val messageID: String
) {
    companion object {
        val XMLNS = "urn:xmpp:reply:0"
        val NAME = "reply"
    }
}

/**
 * Retrieve pointer to the message to which this is a reply to.
 */
fun Element.getReplyToOrNull(): ReplyTo? {
    if (this.name != Message.NAME) return null
    val replyEl = this.getChildrenNS(ReplyTo.NAME, ReplyTo.XMLNS) ?: return null;
    val toAttr = replyEl.attributes["to"]?.toJID() ?: return null
    val idAttr = replyEl.attributes["id"] ?: return null
    return ReplyTo(toAttr, idAttr);
}

/**
 * Mark message as a reply to message to which pointer is passed.
 */
fun Element.setReplyTo(replyTo: ReplyTo?) {
    if (this.name != Message.NAME) return;
    this.getChildrenNS(ReplyTo.NAME, ReplyTo.XMLNS)?.let {
        this.remove(it);
    }
    replyTo?.let {
        this.add(element(ReplyTo.NAME) {
            xmlns = ReplyTo.XMLNS
            attribute("to", it.senderJID.toString())
            attribute("id", it.messageID)
        })
    }
}