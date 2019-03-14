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
package tigase.halcyon.core.xmpp

enum class StanzaType {
	Chat,
	/**
	 * The stanza reports an error that has occurred regarding processing or
	 * delivery of a get or set request.
	 */
	Error,
	/**
	 * The stanza requests information, inquires about what data is needed in
	 * order to complete further operations, etc.
	 */
	Get,
	GroupChat,
	Headline,
	Normal,
	Probe,
	/**
	 * The stanza is a response to a successful get or set request.
	 */
	Result,
	/**
	 * The stanza provides data that is needed for an operation to be completed,
	 * sets new values, replaces existing values, etc.
	 */
	Set,
	Subscribe,
	Subscribed,
	Unavailable,
	Unsubscribe,
	Unsubscribed;
}