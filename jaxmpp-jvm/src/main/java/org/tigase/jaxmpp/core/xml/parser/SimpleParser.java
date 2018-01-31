package org.tigase.jaxmpp.core.xml.parser;
/*
 * SimpleParser.java
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

import java.util.Arrays;

/**
 * <code>SimpleParser</code> - implementation of <em>SAX</em> parser. This is very basic implementation of <em>XML</em>
 * parser designed especially to be light and parse <em>XML</em> streams like jabber <em>XML</em> stream. It is very
 * efficient, capable of parsing parts of <em>XML</em> document received from the network connection as well as handling
 * a few <em>XML</em> documents in one buffer. This is especially useful when parsing data received from the network.
 * Packets received from the network can contain non-comlete <em>XML</em> document as well as a few complete
 * <em>XML</em> documents. It doesn't support <em>XML</em> comments, processing instructions, document inclussions.
 * Actually it supports only: <ul> <li>Start element event (with all attributes found).</li> <li>End element even.</li>
 * <li>Character data event.</li> <li>'OtherXML' data event - everything between '&#60;' and '&#62;' if after &#60; is
 * '?' or '!'. So it can 'catch' doctype declaration, processing instructions but it can't process correctly commented
 * blocks.</li> </ul> Although very simple this imlementation is sufficient for Jabber protocol needs and is even used
 * by some other packages of this server like implementation of <code>UserRepository</code> based on <em>XML</em> file
 * or server configuration. <p>It is worth to note also that this class is fully thread safe. It means that one instance
 * of this class can be simultanously used by many threads. This is to improve resources usage when processing many
 * client connections at the same time.</p> <p> Created: Fri Oct  1 23:02:15 2004 </p>
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class SimpleParser {

	public static final String ATTRIBUTES_NUMBER_LIMIT_PROP_KEY = "tigase.xml.attributes_number_limit";
	public static final String MAX_ATTRIBS_NUMBER_PROP_KEY = "tigase.xml.max_attrib_number";
	public static final String MAX_ELEMENT_NAME_SIZE_PROP_KEY = "tigase.xml.max_element_size";
	public static final String MAX_ATTRIBUTE_NAME_SIZE_PROP_KEY = "tigase.xml.max_attribute_name_size";
	public static final String MAX_ATTRIBUTE_VALUE_SIZE_PROP_KEY = "tigase.xml.max_attribute_value_size";
	public static final String MAX_CDATA_SIZE_PROP_KEY = "tigase.xml.max_cdata_size";
	private static final char OPEN_BRACKET = '<';
	private static final char CLOSE_BRACKET = '>';
	private static final char QUESTION_MARK = '?';
	private static final char EXCLAMATION_MARK = '!';
	private static final char SLASH = '/';
	private static final char SPACE = ' ';
	private static final char TAB = '\t';
	private static final char LF = '\n';
	private static final char CR = '\r';
	private static final char AMP = '&';
	private static final char EQUALS = '=';
	private static final char HASH = '#';
	private static final char SEMICOLON = ';';
	private static final char SINGLE_QUOTE = '\'';
	private static final char DOUBLE_QUOTE = '"';
	private static final char[] QUOTES = {SINGLE_QUOTE, DOUBLE_QUOTE};
	private static final char[] WHITE_CHARS = {SPACE, LF, CR, TAB};
	private static final char[] END_NAME_CHARS = {CLOSE_BRACKET, SLASH, SPACE, TAB, LF, CR};
	private static final char[] ERR_NAME_CHARS = {OPEN_BRACKET, QUESTION_MARK, AMP};
	private static final char[] IGNORE_CHARS = {'\0'};
	private static final boolean[] ALLOWED_CHARS_LOW = new boolean[0x20];

	static {

		// Arrays.sort(WHITE_CHARS);
		Arrays.sort(IGNORE_CHARS);
	}

	static {
		ALLOWED_CHARS_LOW[0x09] = true;
		ALLOWED_CHARS_LOW[0x0A] = true;
		ALLOWED_CHARS_LOW[0x0D] = true;
	}

	public int ATTRIBUTES_NUMBER_LIMIT = 50;
	/**
	 * Variable constant <code>MAX_ATTRIBS_NUMBER</code> keeps value of maximum possible attributes number. Real XML
	 * parser shouldn't have such limit but in most cases XML elements don't have too many attributes. For efficiency it
	 * is better to use fixed number of attributes and operate on arrays than on lists. Data structures will automaticly
	 * grow if real attributes number is bigger so there should be no problem with processing XML streams different than
	 * expected.
	 */
	public int MAX_ATTRIBS_NUMBER = 6;
	public int MAX_ATTRIBUTE_NAME_SIZE = 1024;

	public int MAX_ATTRIBUTE_VALUE_SIZE = 10 * 1024;
	public int MAX_CDATA_SIZE = 1024 * 1024;

	;

	public int MAX_ELEMENT_NAME_SIZE = 1024;

	public SimpleParser() {
		ATTRIBUTES_NUMBER_LIMIT = Integer.getInteger(ATTRIBUTES_NUMBER_LIMIT_PROP_KEY, ATTRIBUTES_NUMBER_LIMIT);
		MAX_ATTRIBS_NUMBER = Integer.getInteger(MAX_ATTRIBS_NUMBER_PROP_KEY, MAX_ATTRIBS_NUMBER);
		MAX_ELEMENT_NAME_SIZE = Integer.getInteger(MAX_ELEMENT_NAME_SIZE_PROP_KEY, MAX_ELEMENT_NAME_SIZE);
		MAX_ATTRIBUTE_NAME_SIZE = Integer.getInteger(MAX_ATTRIBUTE_NAME_SIZE_PROP_KEY, MAX_ATTRIBUTE_NAME_SIZE);
		MAX_ATTRIBUTE_VALUE_SIZE = Integer.getInteger(MAX_ATTRIBUTE_VALUE_SIZE_PROP_KEY, MAX_ATTRIBUTE_VALUE_SIZE);
		MAX_CDATA_SIZE = Integer.getInteger(MAX_CDATA_SIZE_PROP_KEY, MAX_CDATA_SIZE);
	}

	protected boolean checkIsCharValidInXML(ParserState parserState, char chr) {
		boolean highSurrogate = parserState.highSurrogate;
		parserState.highSurrogate = false;
		if (chr <= 0xD7FF) {
			if (chr >= 0x20) {
				return true;
			}
			return ALLOWED_CHARS_LOW[chr];
		} else if (chr <= 0xFFFD) {
			if (chr >= 0xE000) {
				return true;
			}

			if (Character.isLowSurrogate(chr)) {
				return highSurrogate;
			} else if (Character.isHighSurrogate(chr)) {
				parserState.highSurrogate = true;
				return true;
			}
		}
		return false;
	}

	//private boolean ignore(char chr) {
//  return Arrays.binarySearch(IGNORE_CHARS, chr) >= 0;
//}
	private StringBuilder[] initArray(int size) {
		StringBuilder[] array = new StringBuilder[size];

		Arrays.fill(array, null);

		return array;
	}

	private boolean isWhite(char chr) {

		// In most cases the white character is just a space, in such a case
		// below loop would be faster than a binary search
		for (char c : WHITE_CHARS) {
			if (chr == c) {
				return true;
			}
		}

		return false;

		// return Arrays.binarySearch(WHITE_CHARS, chr) >= 0;
	}

	public final void parse(SimpleHandler handler, char[] data, int off, int len) {
		ParserState parser_state = (ParserState) handler.restoreParserState();

		if (parser_state == null) {
			parser_state = new ParserState();
		}    // end of if (parser_state == null)

		for (int index = off; index < len; index++) {
			char chr = data[index];

			// Only one character to ignore right now, let's do it more efficiently
//    if (ignore(chr)) {
//      break;
//    } // end of if (ignore(chr))
//		Replaced by checkCharIsValidInXML()
//			if (chr == IGNORE_CHARS[0]) {
//				break;
//			}
			if (!checkIsCharValidInXML(parser_state, chr)) {
				parser_state.errorMessage = "Not allowed character '" + chr + "' in XML stream";
				parser_state.state = State.ERROR;
			}

			switch (parser_state.state) {
				case START:
					if (chr == OPEN_BRACKET) {
						parser_state.state = State.OPEN_BRACKET;
						parser_state.slash_found = false;
					}    // end of if (chr == OPEN_BRACKET)

					// Skip everything up to open bracket
					break;

				case OPEN_BRACKET:
					switch (chr) {
						case QUESTION_MARK:
						case EXCLAMATION_MARK:
							parser_state.state = State.OTHER_XML;
							parser_state.element_cdata = new StringBuilder(100);
							parser_state.element_cdata.append(chr);

							break;

						case SLASH:
							parser_state.state = State.CLOSE_ELEMENT;
							parser_state.element_name = new StringBuilder(10);
							parser_state.slash_found = true;

							break;

						default:
							if (Arrays.binarySearch(WHITE_CHARS, chr) < 0) {
								if ((chr == ERR_NAME_CHARS[0]) || (chr == ERR_NAME_CHARS[1]) ||
										(chr == ERR_NAME_CHARS[2])) {
									parser_state.state = State.ERROR;
									parser_state.errorMessage = "Not allowed character in start element name: " + chr;

									break;
								}    // end of if ()

								parser_state.state = State.ELEMENT_NAME;
								parser_state.element_name = new StringBuilder(10);
								parser_state.element_name.append(chr);
							}    // end of if ()

							break;
					}        // end of switch (chr)

					break;

				case ELEMENT_NAME:
					if (isWhite(chr)) {
						parser_state.state = State.END_ELEMENT_NAME;

						break;
					}        // end of if ()

					if (chr == SLASH) {
						parser_state.slash_found = true;

						break;
					}        // end of if (chr == SLASH)

					if (chr == CLOSE_BRACKET) {
						parser_state.state = State.ELEMENT_CDATA;
						handler.startElement(parser_state.element_name, null, null);

						if (parser_state.slash_found) {

							// parser_state.state = State.START;
							handler.endElement(parser_state.element_name);
						}

						parser_state.element_name = null;

						break;
					}    // end of if ()

					if ((chr == ERR_NAME_CHARS[0]) || (chr == ERR_NAME_CHARS[1]) || (chr == ERR_NAME_CHARS[2])) {
						parser_state.state = State.ERROR;
						parser_state.errorMessage = "Not allowed character in start element name: " + chr +
								"\nExisting characters in start element name: " + parser_state.element_name.toString();

						break;
					}    // end of if ()

					parser_state.element_name.append(chr);

					if (parser_state.element_name.length() > MAX_ELEMENT_NAME_SIZE) {
						parser_state.state = State.ERROR;
						parser_state.errorMessage =
								"Max element name size exceeded: " + MAX_ELEMENT_NAME_SIZE + "\nreceived: " +
										parser_state.element_name.toString();
					}

					break;

				case CLOSE_ELEMENT:
					if (isWhite(chr)) {
						break;
					}    // end of if ()

					if (chr == SLASH) {
						parser_state.state = State.ERROR;
						parser_state.errorMessage = "Not allowed character in close element name: " + chr +
								"\nExisting characters in close element name: " + parser_state.element_name.toString();

						break;
					}    // end of if (chr == SLASH)

					if (chr == CLOSE_BRACKET) {
						parser_state.state = State.ELEMENT_CDATA;
						if (!handler.endElement(parser_state.element_name)) {
							parser_state.state = State.ERROR;
							parser_state.errorMessage =
									"Malformed XML: element close found without open for this element: " +
											parser_state.element_name;
							break;
						}

						// parser_state = new ParserState();
						parser_state.element_name = null;

						break;
					}    // end of if ()

					if ((chr == ERR_NAME_CHARS[0]) || (chr == ERR_NAME_CHARS[1]) || (chr == ERR_NAME_CHARS[2])) {
						parser_state.state = State.ERROR;
						parser_state.errorMessage = "Not allowed character in close element name: " + chr +
								"\nExisting characters in close element name: " + parser_state.element_name.toString();

						break;
					}    // end of if ()

					parser_state.element_name.append(chr);

					if (parser_state.element_name.length() > MAX_ELEMENT_NAME_SIZE) {
						parser_state.state = State.ERROR;
						parser_state.errorMessage =
								"Max element name size exceeded: " + MAX_ELEMENT_NAME_SIZE + "\nreceived: " +
										parser_state.element_name.toString();
					}

					break;

				case END_ELEMENT_NAME:
					if (chr == SLASH) {
						parser_state.slash_found = true;

						break;
					}    // end of if (chr == SLASH)

					if (chr == CLOSE_BRACKET) {
						parser_state.state = State.ELEMENT_CDATA;
						handler.startElement(parser_state.element_name, parser_state.attrib_names,
											 parser_state.attrib_values);
						parser_state.attrib_names = null;
						parser_state.attrib_values = null;
						parser_state.current_attr = -1;

						if (parser_state.slash_found) {

							// parser_state.state = State.START;
							handler.endElement(parser_state.element_name);
						}

						parser_state.element_name = null;

						break;
					}      // end of if ()

					if (!isWhite(chr)) {
						parser_state.state = State.ATTRIB_NAME;

						if (parser_state.attrib_names == null) {
							parser_state.attrib_names = initArray(MAX_ATTRIBS_NUMBER);
							parser_state.attrib_values = initArray(MAX_ATTRIBS_NUMBER);
						} else {
							if (parser_state.current_attr == parser_state.attrib_names.length - 1) {
								if (parser_state.attrib_names.length >= ATTRIBUTES_NUMBER_LIMIT) {
									parser_state.state = State.ERROR;
									parser_state.errorMessage =
											"Attributes nuber limit exceeded: " + ATTRIBUTES_NUMBER_LIMIT +
													"\nreceived: " + parser_state.element_name.toString();
									break;
								} else {
									int new_size = parser_state.attrib_names.length + MAX_ATTRIBS_NUMBER;

									parser_state.attrib_names = resizeArray(parser_state.attrib_names, new_size);
									parser_state.attrib_values = resizeArray(parser_state.attrib_values, new_size);
								}
							}
						}    // end of else

						parser_state.attrib_names[++parser_state.current_attr] = new StringBuilder(8);
						parser_state.attrib_names[parser_state.current_attr].append(chr);

						break;
					}      // end of if ()

					// do nothing, skip white chars
					break;

				case ATTRIB_NAME:
					if (isWhite(chr) || (chr == EQUALS)) {
						parser_state.state = State.END_OF_ATTR_NAME;

						break;
					}    // end of if ()

					if ((chr == ERR_NAME_CHARS[0]) || (chr == ERR_NAME_CHARS[1]) || (chr == ERR_NAME_CHARS[2])) {
						parser_state.state = State.ERROR;
						parser_state.errorMessage = "Not allowed character in element attribute name: " + chr +
								"\nExisting characters in element attribute name: " +
								parser_state.attrib_names[parser_state.current_attr].toString();

						break;
					}    // end of if ()

					parser_state.attrib_names[parser_state.current_attr].append(chr);

					if (parser_state.attrib_names[parser_state.current_attr].length() > MAX_ATTRIBUTE_NAME_SIZE) {
						parser_state.state = State.ERROR;
						parser_state.errorMessage =
								"Max attribute name size exceeded: " + MAX_ATTRIBUTE_NAME_SIZE + "\nreceived: " +
										parser_state.attrib_names[parser_state.current_attr].toString();
					}

					break;

				case END_OF_ATTR_NAME:
					if (chr == SINGLE_QUOTE) {
						parser_state.state = State.ATTRIB_VALUE_S;
						parser_state.attrib_values[parser_state.current_attr] = new StringBuilder(64);
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)

					if (chr == DOUBLE_QUOTE) {
						parser_state.state = State.ATTRIB_VALUE_D;
						parser_state.attrib_values[parser_state.current_attr] = new StringBuilder(64);
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)

					// Skip white characters and actually everything except quotes
					break;

				case ATTRIB_VALUE_S:
					if (chr == SINGLE_QUOTE) {
						parser_state.state = State.END_ELEMENT_NAME;

						break;
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)

					parser_state.attrib_values[parser_state.current_attr].append(chr);
					switch (chr) {
						case '&':
							parser_state.parentState = parser_state.state;
							parser_state.state = State.ENTITY;
							parser_state.entityType = EntityType.UNKNOWN;
							break;
						case '<':
							parser_state.state = State.ERROR;
							parser_state.errorMessage = "Not allowed character in element attribute value: " + chr +
									"\nExisting characters in element attribute value: " +
									parser_state.attrib_values[parser_state.current_attr].toString();
							break;
						default:
							break;
					}

					if (parser_state.attrib_values[parser_state.current_attr].length() > MAX_ATTRIBUTE_VALUE_SIZE) {
						parser_state.state = State.ERROR;
						parser_state.errorMessage =
								"Max attribute value size exceeded: " + MAX_ATTRIBUTE_VALUE_SIZE + "\nreceived: " +
										parser_state.attrib_values[parser_state.current_attr].toString();
					}

					break;

				case ATTRIB_VALUE_D:
					if (chr == DOUBLE_QUOTE) {
						parser_state.state = State.END_ELEMENT_NAME;

						break;
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)

					parser_state.attrib_values[parser_state.current_attr].append(chr);

					switch (chr) {
						case '&':
							parser_state.parentState = parser_state.state;
							parser_state.state = State.ENTITY;
							parser_state.entityType = EntityType.UNKNOWN;
							break;
						case '<':
							parser_state.state = State.ERROR;
							parser_state.errorMessage = "Not allowed character in element attribute value: " + chr +
									"\nExisting characters in element attribute value: " +
									parser_state.attrib_values[parser_state.current_attr].toString();
							break;
						default:
							break;
					}

					if (parser_state.attrib_values[parser_state.current_attr].length() > MAX_ATTRIBUTE_VALUE_SIZE) {
						parser_state.state = State.ERROR;
						parser_state.errorMessage =
								"Max attribute value size exceeded: " + MAX_ATTRIBUTE_VALUE_SIZE + "\nreceived: " +
										parser_state.attrib_values[parser_state.current_attr].toString();
					}

					break;

				case ELEMENT_CDATA:
					if (chr == OPEN_BRACKET) {
						parser_state.state = State.OPEN_BRACKET;
						parser_state.slash_found = false;

						if (parser_state.element_cdata != null) {
							handler.elementCData(parser_state.element_cdata);
							parser_state.element_cdata = null;
						}    // end of if (parser_state.element_cdata != null)

						break;
					} else {
						if (parser_state.element_cdata == null) {

//            // Skip leading white characters
//            if (Arrays.binarySearch(WHITE_CHARS, chr) < 0) {
							parser_state.element_cdata = new StringBuilder(100);

//            parser_state.element_cdata.append(chr);
//            }// end of if (Arrays.binarySearch(WHITE_CHARS, chr) < 0)
						}    // end of if (parser_state.element_cdata == null) else

						parser_state.element_cdata.append(chr);
						if (chr == '&') {
							parser_state.parentState = parser_state.state;
							parser_state.state = State.ENTITY;
							parser_state.entityType = EntityType.UNKNOWN;
						}

						if (parser_state.element_cdata.length() > MAX_CDATA_SIZE) {
							parser_state.state = State.ERROR;
							parser_state.errorMessage = "Max cdata size exceeded: " + MAX_CDATA_SIZE + "\nreceived: " +
									parser_state.element_cdata.toString();
						}
					}

					break;

				case ENTITY:
					boolean alpha = ((chr >= 'a' && chr <= 'z') || (chr >= 'A' && chr <= 'Z'));
					boolean numeric = !alpha && (chr >= '0' && chr <= '9');

					boolean valid = true;

					switch (parser_state.entityType) {
						case UNKNOWN:
							if (alpha) {
								parser_state.entityType = EntityType.NAMED;
							} else if (chr == HASH) {
								parser_state.entityType = EntityType.CODEPOINT;
							} else {
								valid = false;
							}
							break;
						case NAMED:
							if (!(alpha || numeric)) {
								if (chr != SEMICOLON) {
									valid = false;
								} else {
									parser_state.state = parser_state.parentState;
								}
							}
							break;
						case CODEPOINT:
							if (chr == 'x') {
								parser_state.entityType = EntityType.CODEPOINT_HEX;
							}
							if (numeric) {
								parser_state.entityType = EntityType.CODEPOINT_DEC;
							} else {
								valid = false;
							}
							break;
						case CODEPOINT_DEC:
							if (!numeric) {
								if (chr != SEMICOLON) {
									valid = false;
								} else {
									parser_state.state = parser_state.parentState;
								}
							}
							break;
						case CODEPOINT_HEX:
							if (!((chr >= 'a' && chr <= 'f') || (chr >= 'A' || chr <= 'F') || numeric)) {
								if (chr != SEMICOLON) {
									valid = false;
								} else {
									parser_state.state = parser_state.parentState;
								}
							}
							break;
					}

					if (valid) {
						switch (parser_state.parentState) {
							case ATTRIB_VALUE_D:
							case ATTRIB_VALUE_S:
								parser_state.attrib_values[parser_state.current_attr].append(chr);
								break;
							case ELEMENT_CDATA:
								parser_state.element_cdata.append(chr);
								break;
						}
					} else {
						parser_state.state = State.ERROR;
						parser_state.errorMessage = "Invalid XML entity";
					}
					break;

				case OTHER_XML:
					if (chr == CLOSE_BRACKET) {
						parser_state.state = State.START;
						handler.otherXML(parser_state.element_cdata);
						parser_state.element_cdata = null;

						break;
					}    // end of if (chr == CLOSE_BRACKET)

					if (parser_state.element_cdata == null) {
						parser_state.element_cdata = new StringBuilder(100);
					}    // end of if (parser_state.element_cdata == null) else

					parser_state.element_cdata.append(chr);

					if (parser_state.element_cdata.length() > MAX_CDATA_SIZE) {
						parser_state.state = State.ERROR;
						parser_state.errorMessage = "Max cdata size exceeded: " + MAX_CDATA_SIZE + "\nreceived: " +
								parser_state.element_cdata.toString();
					}

					break;

				case ERROR:
					handler.error(parser_state.errorMessage);
					parser_state = null;

					return;

				// break;
				default:
					assert false : "Unknown SimpleParser state: " + parser_state.state;

					break;
			}    // end of switch (state)
		}      // end of for ()

		handler.saveParserState(parser_state);
	}

	private StringBuilder[] resizeArray(StringBuilder[] src, int size) {
		StringBuilder[] array = new StringBuilder[size];

		System.arraycopy(src, 0, array, 0, src.length);
		Arrays.fill(array, src.length, array.length, null);

		return array;
	}

	protected static enum EntityType {
		UNKNOWN,
		NAMED,
		CODEPOINT,
		CODEPOINT_DEC,
		CODEPOINT_HEX
	}

	protected static enum State {
		START,
		OPEN_BRACKET,
		ELEMENT_NAME,
		END_ELEMENT_NAME,
		ATTRIB_NAME,
		END_OF_ATTR_NAME,
		ATTRIB_VALUE_S,
		ATTRIB_VALUE_D,
		ELEMENT_CDATA,
		OTHER_XML,
		ERROR,
		CLOSE_ELEMENT,
		ENTITY
	}

	protected static class ParserState {

		StringBuilder[] attrib_names = null;
		StringBuilder[] attrib_values = null;
		int current_attr = -1;
		StringBuilder element_cdata = null;
		StringBuilder element_name = null;
		EntityType entityType = EntityType.UNKNOWN;
		String errorMessage = null;
		boolean highSurrogate = false;
		State parentState = null;
		boolean slash_found = false;
		State state = State.START;
	}
}    // SimpleParser


