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

package org.tigase.jaxmpp.core.xml.parser

/**
 * `SimpleParser` - implementation of *SAX* parser. This is very basic implementation of *XML*
 * parser designed especially to be light and parse *XML* streams like jabber *XML* stream. It is very
 * efficient, capable of parsing parts of *XML* document received from the network connection as well as handling
 * a few *XML* documents in one buffer. This is especially useful when parsing data received from the network.
 * Packets received from the network can contain non-comlete *XML* document as well as a few complete
 * *XML* documents. It doesn't support *XML* comments, processing instructions, document inclussions.
 * Actually it supports only:   * Start element event (with all attributes found).  * End element even.
 *  * Character data event.  * 'OtherXML' data event - everything between '&#60;' and '&#62;' if after &#60; is
 * '?' or '!'. So it can 'catch' doctype declaration, processing instructions but it can't process correctly commented
 * blocks.  Although very simple this imlementation is sufficient for Jabber protocol needs and is even used
 * by some other packages of this server like implementation of `UserRepository` based on *XML* file
 * or server configuration.
 *
 *It is worth to note also that this class is fully thread safe. It means that one instance
 * of this class can be simultanously used by many threads. This is to improve resources usage when processing many
 * client connections at the same time.
 *
 * Created: Fri Oct  1 23:02:15 2004
 *
 * @author [Artur Hefczyc](mailto:artur.hefczyc@tigase.org)
 * @version $Rev$
 */
class SimpleParser {

	var ATTRIBUTES_NUMBER_LIMIT = 50
	/**
	 * Variable constant `MAX_ATTRIBS_NUMBER` keeps value of maximum possible attributes number. Real XML
	 * parser shouldn't have such limit but in most cases XML elements don't have too many attributes. For efficiency it
	 * is better to use fixed number of attributes and operate on arrays than on lists. Data structures will automaticly
	 * grow if real attributes number is bigger so there should be no problem with processing XML streams different than
	 * expected.
	 */
	var MAX_ATTRIBS_NUMBER = 6
	var MAX_ATTRIBUTE_NAME_SIZE = 1024

	var MAX_ATTRIBUTE_VALUE_SIZE = 10 * 1024
	var MAX_CDATA_SIZE = 1024 * 1024

	var MAX_ELEMENT_NAME_SIZE = 1024

	protected fun checkIsCharValidInXML(parserState: ParserState?, chr: Char): Boolean {
		val highSurrogate = parserState!!.highSurrogate
		parserState.highSurrogate = false
		if (chr.toInt() <= 0xD7FF) {
			return if (chr.toInt() >= 0x20) {
				true
			} else ALLOWED_CHARS_LOW[chr.toInt()]
		} else if (chr.toInt() <= 0xFFFD) {
			if (chr.toInt() >= 0xE000) {
				return true
			}

			if (chr.isLowSurrogate()) {
				return highSurrogate
			} else if (chr.isHighSurrogate()) {
				parserState.highSurrogate = true
				return true
			}
		}
		return false
	}

	//private boolean ignore(char chr) {
	//  return Arrays.binarySearch(IGNORE_CHARS, chr) >= 0;
	//}
	private fun initArray(size: Int): Array<StringBuilder?> = arrayOfNulls<StringBuilder>(size)

	private fun isWhite(chr: Char): Boolean {

		// In most cases the white character is just a space, in such a case
		// below loop would be faster than a binary search
		for (c in WHITE_CHARS) {
			if (chr == c) {
				return true
			}
		}

		return false

		// return Arrays.binarySearch(WHITE_CHARS, chr) >= 0;
	}

	fun parse(handler: SimpleHandler, data: CharArray) {
		parse(handler, data, 0, data.size - 1)
	}

	fun parse(handler: SimpleHandler, data: CharArray, offset: Int, len: Int) {
		var parser_state = handler.restoreParserState() as ParserState?

		if (parser_state == null) {
			parser_state = ParserState()
		}    // end of if (parser_state == null)

		LOOP@ for (idx in offset..offset + len) {
			val chr = data[idx]
//		for (index in off until len) {
//			val chr Char = data[index]

			// Only one character to ignore right now, let's do it more efficiently
			//    if (ignore(chr)) {
			//      break;
			//    } // end of if (ignore(chr))
			//		Replaced by checkCharIsValidInXML()
			//			if (chr == IGNORE_CHARS[0]) {
			//				break;
			//			}
			if (!checkIsCharValidInXML(parser_state, chr)) {
				parser_state!!.errorMessage = "Not allowed character '$chr' in XML stream"
				parser_state.state = State.ERROR
			}

			STATE@ when (parser_state!!.state) {
				SimpleParser.State.START -> if (chr == OPEN_BRACKET) {
					parser_state.state = State.OPEN_BRACKET
					parser_state.slash_found = false
				}    // end of if (chr == OPEN_BRACKET)

				SimpleParser.State.OPEN_BRACKET -> when (chr) {
					QUESTION_MARK, EXCLAMATION_MARK -> {
						parser_state.state = State.OTHER_XML
						parser_state.element_cdata = StringBuilder(100)
						parser_state.element_cdata!!.append(chr)
					}

					SLASH -> {
						parser_state.state = State.CLOSE_ELEMENT
						parser_state.element_name = StringBuilder(10)
						parser_state.slash_found = true
					}

					else -> if (!WHITE_CHARS.contains(chr)) {
						if (chr == ERR_NAME_CHARS[0] || chr == ERR_NAME_CHARS[1] || chr == ERR_NAME_CHARS[2]) {
							parser_state.state = State.ERROR
							parser_state.errorMessage = "Not allowed character in start element name: " + chr
						} else {
							parser_state.state = State.ELEMENT_NAME
							parser_state.element_name = StringBuilder(10)
							parser_state.element_name!!.append(chr)
						}
					} // end of if ()
				}        // end of switch (chr)

				SimpleParser.State.ELEMENT_NAME -> {
					if (isWhite(chr)) {
						parser_state.state = State.END_ELEMENT_NAME

						continue@LOOP
					}        // end of if ()

					if (chr == SLASH) {
						parser_state.slash_found = true

						continue@LOOP
					}        // end of if (chr == SLASH)

					if (chr == CLOSE_BRACKET) {
						parser_state.state = State.ELEMENT_CDATA
						handler.startElement(parser_state.element_name!!.toString(), null, null)

						if (parser_state.slash_found) {

							// parser_state.state = State.START;
							handler.endElement(parser_state.element_name!!.toString())
						}

						parser_state.element_name = null

						continue@LOOP
					}    // end of if ()

					if (chr == ERR_NAME_CHARS[0] || chr == ERR_NAME_CHARS[1] || chr == ERR_NAME_CHARS[2]) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Not allowed character in start element name: " + chr + "\nExisting characters in start element name: " + parser_state.element_name!!.toString()

						continue@LOOP
					}    // end of if ()

					parser_state.element_name!!.append(chr)

					if (parser_state.element_name!!.length > MAX_ELEMENT_NAME_SIZE) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Max element name size exceeded: " + MAX_ELEMENT_NAME_SIZE + "\nreceived: " + parser_state.element_name!!.toString()
					}
				}

				SimpleParser.State.CLOSE_ELEMENT -> {
					if (isWhite(chr)) {
						continue@LOOP
					}    // end of if ()

					if (chr == SLASH) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Not allowed character in close element name: " + chr + "\nExisting characters in close element name: " + parser_state.element_name!!.toString()

						continue@LOOP
					}    // end of if (chr == SLASH)

					if (chr == CLOSE_BRACKET) {
						parser_state.state = State.ELEMENT_CDATA
						if (!handler.endElement(parser_state.element_name!!.toString())) {
							parser_state.state = State.ERROR
							parser_state.errorMessage = "Malformed XML: element close found without open for this element: " + parser_state.element_name!!
							continue@LOOP
						}

						// parser_state = new ParserState();
						parser_state.element_name = null

						continue@LOOP
					}    // end of if ()

					if (chr == ERR_NAME_CHARS[0] || chr == ERR_NAME_CHARS[1] || chr == ERR_NAME_CHARS[2]) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Not allowed character in close element name: " + chr + "\nExisting characters in close element name: " + parser_state.element_name!!.toString()

						continue@LOOP
					}    // end of if ()

					parser_state.element_name!!.append(chr)

					if (parser_state.element_name!!.length > MAX_ELEMENT_NAME_SIZE) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Max element name size exceeded: " + MAX_ELEMENT_NAME_SIZE + "\nreceived: " + parser_state.element_name!!.toString()
					}
				}

				SimpleParser.State.END_ELEMENT_NAME -> {
					if (chr == SLASH) {
						parser_state.slash_found = true

						continue@LOOP
					}    // end of if (chr == SLASH)

					if (chr == CLOSE_BRACKET) {
						parser_state.state = State.ELEMENT_CDATA
						handler.startElement(parser_state.element_name!!.toString(),
											 toStringArray(parser_state.attrib_names),
											 toStringArray(parser_state.attrib_values))
						parser_state.attrib_names = null
						parser_state.attrib_values = null
						parser_state.current_attr = -1

						if (parser_state.slash_found) {

							// parser_state.state = State.START;
							handler.endElement(parser_state.element_name!!.toString())
						}

						parser_state.element_name = null

						continue@LOOP
					}      // end of if ()

					if (!isWhite(chr)) {
						parser_state.state = State.ATTRIB_NAME

						if (parser_state.attrib_names == null) {
							parser_state.attrib_names = initArray(MAX_ATTRIBS_NUMBER)
							parser_state.attrib_values = initArray(MAX_ATTRIBS_NUMBER)
						} else {
							if (parser_state.current_attr == parser_state.attrib_names!!.size - 1) {
								if (parser_state.attrib_names!!.size >= ATTRIBUTES_NUMBER_LIMIT) {
									parser_state.state = State.ERROR
									parser_state.errorMessage = "Attributes nuber limit exceeded: " + ATTRIBUTES_NUMBER_LIMIT + "\nreceived: " + parser_state.element_name!!.toString()
									continue@LOOP
								} else {
									val new_size = parser_state.attrib_names!!.size + MAX_ATTRIBS_NUMBER

									parser_state.attrib_names = resizeArray(parser_state.attrib_names, new_size)
									parser_state.attrib_values = resizeArray(parser_state.attrib_values, new_size)
								}
							}
						}    // end of else

						parser_state.attrib_names!![++parser_state.current_attr] = StringBuilder(8)
						parser_state.attrib_names!![parser_state.current_attr]!!.append(chr)

						continue@LOOP
					}      // end of if ()
				}

				SimpleParser.State.ATTRIB_NAME -> {
					if (isWhite(chr) || chr == EQUALS) {
						parser_state.state = State.END_OF_ATTR_NAME

						continue@LOOP
					}    // end of if ()

					if (chr == ERR_NAME_CHARS[0] || chr == ERR_NAME_CHARS[1] || chr == ERR_NAME_CHARS[2]) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Not allowed character in element attribute name: " + chr + "\nExisting characters in element attribute name: " + parser_state.attrib_names!![parser_state.current_attr].toString()

						continue@LOOP
					}    // end of if ()

					parser_state.attrib_names!![parser_state.current_attr]!!.append(chr)

					if (parser_state.attrib_names!![parser_state.current_attr]!!.length > MAX_ATTRIBUTE_NAME_SIZE) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Max attribute name size exceeded: " + MAX_ATTRIBUTE_NAME_SIZE + "\nreceived: " + parser_state.attrib_names!![parser_state.current_attr].toString()
					}
				}

				SimpleParser.State.END_OF_ATTR_NAME -> {
					if (chr == SINGLE_QUOTE) {
						parser_state.state = State.ATTRIB_VALUE_S
						parser_state.attrib_values!![parser_state.current_attr] = StringBuilder(64)
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)

					if (chr == DOUBLE_QUOTE) {
						parser_state.state = State.ATTRIB_VALUE_D
						parser_state.attrib_values!![parser_state.current_attr] = StringBuilder(64)
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)
				}

				SimpleParser.State.ATTRIB_VALUE_S -> {
					if (chr == SINGLE_QUOTE) {
						parser_state.state = State.END_ELEMENT_NAME

						continue@LOOP
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)

					parser_state.attrib_values!![parser_state.current_attr]!!.append(chr)
					when (chr) {
						'&' -> {
							parser_state.parentState = parser_state.state
							parser_state.state = State.ENTITY
							parser_state.entityType = EntityType.UNKNOWN
						}
						'<' -> {
							parser_state.state = State.ERROR
							parser_state.errorMessage = "Not allowed character in element attribute value: " + chr + "\nExisting characters in element attribute value: " + parser_state.attrib_values!![parser_state.current_attr].toString()
						}
						else -> {
						}
					}

					if (parser_state.attrib_values!![parser_state.current_attr]!!.length > MAX_ATTRIBUTE_VALUE_SIZE) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Max attribute value size exceeded: " + MAX_ATTRIBUTE_VALUE_SIZE + "\nreceived: " + parser_state.attrib_values!![parser_state.current_attr].toString()
					}
				}

				SimpleParser.State.ATTRIB_VALUE_D -> {
					if (chr == DOUBLE_QUOTE) {
						parser_state.state = State.END_ELEMENT_NAME

						continue@LOOP
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)

					parser_state.attrib_values!![parser_state.current_attr]!!.append(chr)

					when (chr) {
						'&' -> {
							parser_state.parentState = parser_state.state
							parser_state.state = State.ENTITY
							parser_state.entityType = EntityType.UNKNOWN
						}
						'<' -> {
							parser_state.state = State.ERROR
							parser_state.errorMessage = "Not allowed character in element attribute value: " + chr + "\nExisting characters in element attribute value: " + parser_state.attrib_values!![parser_state.current_attr].toString()
						}
						else -> {
						}
					}

					if (parser_state.attrib_values!![parser_state.current_attr]!!.length > MAX_ATTRIBUTE_VALUE_SIZE) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Max attribute value size exceeded: " + MAX_ATTRIBUTE_VALUE_SIZE + "\nreceived: " + parser_state.attrib_values!![parser_state.current_attr].toString()
					}
				}

				SimpleParser.State.ELEMENT_CDATA -> if (chr == OPEN_BRACKET) {
					parser_state.state = State.OPEN_BRACKET
					parser_state.slash_found = false

					if (parser_state.element_cdata != null) {
						handler.elementCData(parser_state.element_cdata!!.toString())
						parser_state.element_cdata = null
					}    // end of if (parser_state.element_cdata != null)

					continue@LOOP
				} else {
					if (parser_state.element_cdata == null) {

						//            // Skip leading white characters
						//            if (Arrays.binarySearch(WHITE_CHARS, chr) < 0) {
						parser_state.element_cdata = StringBuilder(100)

						//            parser_state.element_cdata.append(chr);
						//            }// end of if (Arrays.binarySearch(WHITE_CHARS, chr) < 0)
					}    // end of if (parser_state.element_cdata == null) else

					parser_state.element_cdata!!.append(chr)
					if (chr == '&') {
						parser_state.parentState = parser_state.state
						parser_state.state = State.ENTITY
						parser_state.entityType = EntityType.UNKNOWN
					}

					if (parser_state.element_cdata!!.length > MAX_CDATA_SIZE) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Max cdata size exceeded: " + MAX_CDATA_SIZE + "\nreceived: " + parser_state.element_cdata!!.toString()
					}
				}

				SimpleParser.State.ENTITY -> {
					val alpha = chr >= 'a' && chr <= 'z' || chr >= 'A' && chr <= 'Z'
					val numeric = !alpha && chr >= '0' && chr <= '9'

					var valid = true

					when (parser_state.entityType) {
						SimpleParser.EntityType.UNKNOWN -> if (alpha) {
							parser_state.entityType = EntityType.NAMED
						} else if (chr == HASH) {
							parser_state.entityType = EntityType.CODEPOINT
						} else {
							valid = false
						}
						SimpleParser.EntityType.NAMED -> if (!(alpha || numeric)) {
							if (chr != SEMICOLON) {
								valid = false
							} else {
								parser_state.state = parser_state.parentState
							}
						}
						SimpleParser.EntityType.CODEPOINT -> {
							if (chr == 'x') {
								parser_state.entityType = EntityType.CODEPOINT_HEX
							}
							if (numeric) {
								parser_state.entityType = EntityType.CODEPOINT_DEC
							} else {
								valid = false
							}
						}
						SimpleParser.EntityType.CODEPOINT_DEC -> if (!numeric) {
							if (chr != SEMICOLON) {
								valid = false
							} else {
								parser_state.state = parser_state.parentState
							}
						}
						SimpleParser.EntityType.CODEPOINT_HEX -> if (!(chr >= 'a' && chr <= 'f' || chr >= 'A' || chr <= 'F' || numeric)) {
							if (chr != SEMICOLON) {
								valid = false
							} else {
								parser_state.state = parser_state.parentState
							}
						}
					}

					if (valid) {
						when (parser_state.parentState) {
							SimpleParser.State.ATTRIB_VALUE_D, SimpleParser.State.ATTRIB_VALUE_S -> parser_state.attrib_values!![parser_state.current_attr]!!.append(
									chr)
							SimpleParser.State.ELEMENT_CDATA -> parser_state.element_cdata!!.append(chr)
						}
					} else {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Invalid XML entity"
					}
				}

				SimpleParser.State.OTHER_XML -> {
					if (chr == CLOSE_BRACKET) {
						parser_state.state = State.START
						handler.otherXML(parser_state.element_cdata!!.toString())
						parser_state.element_cdata = null

						//continue@LOOP
						continue@LOOP
					}    // end of if (chr == CLOSE_BRACKET)

					if (parser_state.element_cdata == null) {
						parser_state.element_cdata = StringBuilder(100)
					}    // end of if (parser_state.element_cdata == null) else

					parser_state.element_cdata!!.append(chr)

					if (parser_state.element_cdata!!.length > MAX_CDATA_SIZE) {
						parser_state.state = State.ERROR
						parser_state.errorMessage = "Max cdata size exceeded: " + MAX_CDATA_SIZE + "\nreceived: " + parser_state.element_cdata!!.toString()
					}
				}

				SimpleParser.State.ERROR -> {
					handler.error(parser_state.errorMessage!!)
					parser_state = null

					return
				}

			// break;
				else -> throw RuntimeException("Unknown SimpleParser state: " + parser_state.state)
			}// Skip everything up to open bracket
			// do nothing, skip white chars
			// Skip white characters and actually everything except quotes
			// end of switch (state)
		}      // end of for ()

		handler.saveParserState(parser_state)
	}

	private fun toStringArray(src: Array<StringBuilder?>?): Array<String?>? {
		if (src == null) return null
		val res = arrayOfNulls<String>(src!!.size)
		for (i: Int in 0 until src.size) res[i] = when {
			src[i] == null -> null
			else -> src[i].toString()
		}
		return res
	}

	private fun resizeArray(src: Array<StringBuilder?>?, size: Int): Array<StringBuilder?> = src!!.copyOf(size)

	protected enum class EntityType {
		UNKNOWN,
		NAMED,
		CODEPOINT,
		CODEPOINT_DEC,
		CODEPOINT_HEX
	}

	protected enum class State {
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

	protected class ParserState {
		internal var attrib_names: Array<StringBuilder?>? = null
		internal var attrib_values: Array<StringBuilder?>? = null
		internal var current_attr = -1
		internal var element_cdata: StringBuilder? = null
		internal var element_name: StringBuilder? = null
		internal var entityType = EntityType.UNKNOWN
		internal var errorMessage: String? = null
		internal var highSurrogate = false
		internal var parentState: State? = null
		internal var slash_found = false
		internal var state: State? = State.START
	}

	companion object {
		private val OPEN_BRACKET = '<'
		private val CLOSE_BRACKET = '>'
		private val QUESTION_MARK = '?'
		private val EXCLAMATION_MARK = '!'
		private val SLASH = '/'
		private val SPACE = ' '
		private val TAB = '\t'
		private val LF = '\n'
		private val CR = '\r'
		private val AMP = '&'
		private val EQUALS = '='
		private val HASH = '#'
		private val SEMICOLON = ';'
		private val SINGLE_QUOTE = '\''
		private val DOUBLE_QUOTE = '"'
		private val QUOTES = charArrayOf(SINGLE_QUOTE, DOUBLE_QUOTE).sortedArray()
		private val WHITE_CHARS = charArrayOf(SPACE, LF, CR, TAB).sortedArray()
		private val END_NAME_CHARS = charArrayOf(CLOSE_BRACKET, SLASH, SPACE, TAB, LF, CR).sortedArray()
		private val ERR_NAME_CHARS = charArrayOf(OPEN_BRACKET, QUESTION_MARK, AMP).sortedArray()
		private val IGNORE_CHARS = charArrayOf('\u0000').sortedArray()
		private val ALLOWED_CHARS_LOW = BooleanArray(0x20)

		init {
			ALLOWED_CHARS_LOW[0x09] = true
			ALLOWED_CHARS_LOW[0x0A] = true
			ALLOWED_CHARS_LOW[0x0D] = true
		}
	}
}    // SimpleParser


