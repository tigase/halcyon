/*
 * SimpleHandler.java
 *
 * Tigase Jabber/XMPP XML Tools
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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

package org.tigase.jaxmpp.core.xml.parser;

/**
 * <code>SimpleHandler</code> - parser handler interface for event driven parser. It is very simplified version of
 * <code>org.xml.sax.ContentHandler</code> interface created for <code>SimpleParser</code> needs. It allows to receive
 * events like start element (with element attributes), end element, element cdata, other XML content and error event if
 * XML error found.
 * <p> Created: Sat Oct  2 00:00:08 2004 </p>
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 * @see SimpleParser
 */

public interface SimpleHandler {

	void elementCData(StringBuilder cdata);

	boolean endElement(StringBuilder name);

	void error(String errorMessage);

	void otherXML(StringBuilder other);

	Object restoreParserState();

	void saveParserState(Object state);

	void startElement(StringBuilder name, StringBuilder[] attr_names, StringBuilder[] attr_values);

}// SimpleHandler
