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

package tigase.halcyon.core.xml.parser

/**
 * `SimpleParser` - implementation of *SAX* parser. This is very basic implementation of *XML*
 * parser designed especially to be light and parse *XML* streams like jabber *XML* stream. It is very
 * efficient, capable of parsing parts of *XML* document received from the network connection as well as handling
 * a few *XML* documents in one buffer. This is especially useful when parsing data received from the network.
 * Packets received from the network can contain non-complete *XML* document as well as a few complete
 * *XML* documents. It doesn't support *XML* comments, processing instructions, document inclusions.
 * Actually it supports only:   * Start element event (with all attributes found).  * End element even.
 *  * Character data event.  * 'OtherXML' data event - everything between '&#60;' and '&#62;' if after &#60; is
 * '?' or '!'. So it can 'catch' doctype declaration, processing instructions but it can't process correctly commented
 * blocks.  Although very simple this implementation is sufficient for Jabber protocol needs and is even used
 * by some other packages of this server like implementation of `UserRepository` based on *XML* file
 * or server configuration.
 *
 * It is worth to note also that this class is fully thread safe. It means that one instance
 * of this class can be simultaneously used by many threads. This is to improve resources usage when processing many
 * client connections at the same time.
 *
 * Created: Fri Oct  1 23:02:15 2004
 *
 * @author [Artur Hefczyc](mailto:artur.hefczyc@tigase.net)
 * @version $Rev$
 */
class SimpleParser {

	var attributesNumberLimit = 50

	/**
	 * Variable constant `MAX_ATTRIBS_NUMBER` keeps value of maximum possible attributes number. Real XML
	 * parser shouldn't have such limit but in most cases XML elements don't have too many attributes. For efficiency it
	 * is better to use fixed number of attributes and operate on arrays than on lists. Data structures will automatically
	 * grow if real attributes number is bigger so there should be no problem with processing XML streams different than
	 * expected.
	 */
	var maxAttribsNumber = 6
	var maxAttributeNameSize = 1024

	var maxAttributeValueSize = 10 * 1024
	var maxCdataSize = 1024 * 1024

	var maxElementNameSize = 1024

	private fun checkIsCharValidInXML(parserState: ParserState?, chr: Char): Boolean {
		val highSurrogate = parserState!!.highSurrogate
		parserState.highSurrogate = false
		if (chr.code <= 0xD7FF) {
			return if (chr.code >= 0x20) {
				true
			} else ALLOWED_CHARS_LOW[chr.code]
		} else if (chr.code <= 0xFFFD) {
			if (chr.code >= 0xE000) {
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
	private fun initArray(size: Int): Array<StringBuilder?> = arrayOfNulls(size)

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
		var parserState = handler.restoreParserState() as ParserState?

		if (parserState == null) {
			parserState = ParserState()
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
			if (!checkIsCharValidInXML(parserState, chr)) {
				parserState.errorMessage = "Not allowed character '$chr' in XML stream"
				parserState.state = State.ERROR
			}

			when (parserState.state) {
				State.START -> if (chr == OPEN_BRACKET) {
					parserState.state = State.OPEN_BRACKET
					parserState.slash_found = false
				}    // end of if (chr == OPEN_BRACKET)

				State.OPEN_BRACKET -> when (chr) {
					QUESTION_MARK, EXCLAMATION_MARK -> {
						parserState.state = State.OTHER_XML
						parserState.element_cdata = StringBuilder(100)
						parserState.element_cdata!!.append(chr)
					}

					SLASH -> {
						parserState.state = State.CLOSE_ELEMENT
						parserState.element_name = StringBuilder(10)
						parserState.slash_found = true
					}

					else -> if (!WHITE_CHARS.contains(chr)) {
						if (chr == ERR_NAME_CHARS[0] || chr == ERR_NAME_CHARS[1] || chr == ERR_NAME_CHARS[2]) {
							parserState.state = State.ERROR
							parserState.errorMessage = "Not allowed character in start element name: " + chr
						} else {
							parserState.state = State.ELEMENT_NAME
							parserState.element_name = StringBuilder(10)
							parserState.element_name!!.append(chr)
						}
					} // end of if ()
				}        // end of switch (chr)

				State.ELEMENT_NAME -> {
					if (isWhite(chr)) {
						parserState.state = State.END_ELEMENT_NAME

						continue@LOOP
					}        // end of if ()

					if (chr == SLASH) {
						parserState.slash_found = true

						continue@LOOP
					}        // end of if (chr == SLASH)

					if (chr == CLOSE_BRACKET) {
						parserState.state = State.ELEMENT_CDATA
						handler.startElement(parserState.element_name!!.toString(), null, null)

						if (parserState.slash_found) {

							// parser_state.state = State.START;
							handler.endElement(parserState.element_name!!.toString())
						}

						parserState.element_name = null

						continue@LOOP
					}    // end of if ()

					if (chr == ERR_NAME_CHARS[0] || chr == ERR_NAME_CHARS[1] || chr == ERR_NAME_CHARS[2]) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Not allowed character in start element name: " + chr + "\nExisting characters in start element name: " + parserState.element_name!!.toString()

						continue@LOOP
					}    // end of if ()

					parserState.element_name!!.append(chr)

					if (parserState.element_name!!.length > maxElementNameSize) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Max element name size exceeded: " + maxElementNameSize + "\nreceived: " + parserState.element_name!!.toString()
					}
				}

				State.CLOSE_ELEMENT -> {
					if (isWhite(chr)) {
						continue@LOOP
					}    // end of if ()

					if (chr == SLASH) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Not allowed character in close element name: " + chr + "\nExisting characters in close element name: " + parserState.element_name!!.toString()

						continue@LOOP
					}    // end of if (chr == SLASH)

					if (chr == CLOSE_BRACKET) {
						parserState.state = State.ELEMENT_CDATA
						if (!handler.endElement(parserState.element_name!!.toString())) {
							parserState.state = State.ERROR
							parserState.errorMessage =
								"Malformed XML: element close found without open for this element: " + parserState.element_name!!
							continue@LOOP
						}

						// parser_state = new ParserState();
						parserState.element_name = null

						continue@LOOP
					}    // end of if ()

					if (chr == ERR_NAME_CHARS[0] || chr == ERR_NAME_CHARS[1] || chr == ERR_NAME_CHARS[2]) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Not allowed character in close element name: " + chr + "\nExisting characters in close element name: " + parserState.element_name!!.toString()

						continue@LOOP
					}    // end of if ()

					parserState.element_name!!.append(chr)

					if (parserState.element_name!!.length > maxElementNameSize) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Max element name size exceeded: " + maxElementNameSize + "\nreceived: " + parserState.element_name!!.toString()
					}
				}

				State.END_ELEMENT_NAME -> {
					if (chr == SLASH) {
						parserState.slash_found = true

						continue@LOOP
					}    // end of if (chr == SLASH)

					if (chr == CLOSE_BRACKET) {
						parserState.state = State.ELEMENT_CDATA
						handler.startElement(
							parserState.element_name!!.toString(),
							toStringArray(parserState.attrib_names),
							toStringArray(parserState.attrib_values)
						)
						parserState.attrib_names = null
						parserState.attrib_values = null
						parserState.current_attr = -1

						if (parserState.slash_found) {

							// parser_state.state = State.START;
							handler.endElement(parserState.element_name!!.toString())
						}

						parserState.element_name = null

						continue@LOOP
					}      // end of if ()

					if (!isWhite(chr)) {
						parserState.state = State.ATTRIB_NAME

						if (parserState.attrib_names == null) {
							parserState.attrib_names = initArray(maxAttribsNumber)
							parserState.attrib_values = initArray(maxAttribsNumber)
						} else {
							if (parserState.current_attr == parserState.attrib_names!!.size - 1) {
								if (parserState.attrib_names!!.size >= attributesNumberLimit) {
									parserState.state = State.ERROR
									parserState.errorMessage =
										"Attributes nuber limit exceeded: " + attributesNumberLimit + "\nreceived: " + parserState.element_name!!.toString()
									continue@LOOP
								} else {
									val newSize = parserState.attrib_names!!.size + maxAttribsNumber

									parserState.attrib_names = resizeArray(parserState.attrib_names, newSize)
									parserState.attrib_values = resizeArray(parserState.attrib_values, newSize)
								}
							}
						}    // end of else

						parserState.attrib_names!![++parserState.current_attr] = StringBuilder(8)
						parserState.attrib_names!![parserState.current_attr]!!.append(chr)

						continue@LOOP
					}      // end of if ()
				}

				State.ATTRIB_NAME -> {
					if (isWhite(chr) || chr == EQUALS) {
						parserState.state = State.END_OF_ATTR_NAME

						continue@LOOP
					}    // end of if ()

					if (chr == ERR_NAME_CHARS[0] || chr == ERR_NAME_CHARS[1] || chr == ERR_NAME_CHARS[2]) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Not allowed character in element attribute name: " + chr + "\nExisting characters in element attribute name: " + parserState.attrib_names!![parserState.current_attr].toString()

						continue@LOOP
					}    // end of if ()

					parserState.attrib_names!![parserState.current_attr]!!.append(chr)

					if (parserState.attrib_names!![parserState.current_attr]!!.length > maxAttributeNameSize) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Max attribute name size exceeded: " + maxAttributeNameSize + "\nreceived: " + parserState.attrib_names!![parserState.current_attr].toString()
					}
				}

				State.END_OF_ATTR_NAME -> {
					if (chr == SINGLE_QUOTE) {
						parserState.state = State.ATTRIB_VALUE_S
						parserState.attrib_values!![parserState.current_attr] = StringBuilder(64)
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)

					if (chr == DOUBLE_QUOTE) {
						parserState.state = State.ATTRIB_VALUE_D
						parserState.attrib_values!![parserState.current_attr] = StringBuilder(64)
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)
				}

				State.ATTRIB_VALUE_S -> {
					if (chr == SINGLE_QUOTE) {
						parserState.state = State.END_ELEMENT_NAME

						continue@LOOP
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)

					parserState.attrib_values!![parserState.current_attr]!!.append(chr)
					when (chr) {
						'&' -> {
							parserState.parentState = parserState.state
							parserState.state = State.ENTITY
							parserState.entityType = EntityType.UNKNOWN
						}

						'<' -> {
							parserState.state = State.ERROR
							parserState.errorMessage =
								"Not allowed character in element attribute value: " + chr + "\nExisting characters in element attribute value: " + parserState.attrib_values!![parserState.current_attr].toString()
						}

						else -> {
						}
					}

					if (parserState.attrib_values!![parserState.current_attr]!!.length > maxAttributeValueSize) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Max attribute value size exceeded: " + maxAttributeValueSize + "\nreceived: " + parserState.attrib_values!![parserState.current_attr].toString()
					}
				}

				State.ATTRIB_VALUE_D -> {
					if (chr == DOUBLE_QUOTE) {
						parserState.state = State.END_ELEMENT_NAME

						continue@LOOP
					}    // end of if (chr == SINGLE_QUOTE || chr == DOUBLE_QUOTE)

					parserState.attrib_values!![parserState.current_attr]!!.append(chr)

					when (chr) {
						'&' -> {
							parserState.parentState = parserState.state
							parserState.state = State.ENTITY
							parserState.entityType = EntityType.UNKNOWN
						}

						'<' -> {
							parserState.state = State.ERROR
							parserState.errorMessage =
								"Not allowed character in element attribute value: " + chr + "\nExisting characters in element attribute value: " + parserState.attrib_values!![parserState.current_attr].toString()
						}

						else -> {
						}
					}

					if (parserState.attrib_values!![parserState.current_attr]!!.length > maxAttributeValueSize) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Max attribute value size exceeded: " + maxAttributeValueSize + "\nreceived: " + parserState.attrib_values!![parserState.current_attr].toString()
					}
				}

				State.ELEMENT_CDATA -> if (chr == OPEN_BRACKET) {
					parserState.state = State.OPEN_BRACKET
					parserState.slash_found = false

					if (parserState.element_cdata != null) {
						handler.elementCData(parserState.element_cdata!!.toString())
						parserState.element_cdata = null
					}    // end of if (parser_state.element_cdata != null)

					continue@LOOP
				} else {
					if (parserState.element_cdata == null) {

						//            // Skip leading white characters
						//            if (Arrays.binarySearch(WHITE_CHARS, chr) < 0) {
						parserState.element_cdata = StringBuilder(100)

						//            parser_state.element_cdata.append(chr);
						//            }// end of if (Arrays.binarySearch(WHITE_CHARS, chr) < 0)
					}    // end of if (parser_state.element_cdata == null) else

					parserState.element_cdata!!.append(chr)
					if (chr == '&') {
						parserState.parentState = parserState.state
						parserState.state = State.ENTITY
						parserState.entityType = EntityType.UNKNOWN
					}

					if (parserState.element_cdata!!.length > maxCdataSize) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Max cdata size exceeded: " + maxCdataSize + "\nreceived: " + parserState.element_cdata!!.toString()
					}
				}

				State.ENTITY -> {
					val alpha = chr >= 'a' && chr <= 'z' || chr >= 'A' && chr <= 'Z'
					val numeric = !alpha && chr >= '0' && chr <= '9'

					var valid = true

					when (parserState.entityType) {
						EntityType.UNKNOWN -> if (alpha) {
							parserState.entityType = EntityType.NAMED
						} else if (chr == HASH) {
							parserState.entityType = EntityType.CODEPOINT
						} else {
							valid = false
						}

						EntityType.NAMED -> if (!(alpha || numeric)) {
							if (chr != SEMICOLON) {
								valid = false
							} else {
								parserState.state = parserState.parentState
							}
						}

						EntityType.CODEPOINT -> {
							if (chr == 'x') {
								parserState.entityType = EntityType.CODEPOINT_HEX
							}
							if (numeric) {
								parserState.entityType = EntityType.CODEPOINT_DEC
							} else {
								valid = false
							}
						}

						EntityType.CODEPOINT_DEC -> if (!numeric) {
							if (chr != SEMICOLON) {
								valid = false
							} else {
								parserState.state = parserState.parentState
							}
						}

						EntityType.CODEPOINT_HEX -> if (!(chr >= 'a' && chr <= 'f' || chr >= 'A' || chr <= 'F' || numeric)) {
							if (chr != SEMICOLON) {
								valid = false
							} else {
								parserState.state = parserState.parentState
							}
						}
					}

					if (valid) {
						if (parserState.parentState == State.ATTRIB_VALUE_D || parserState.parentState == State.ATTRIB_VALUE_S) parserState.attrib_values!![parserState.current_attr]!!.append(
							chr
						)
						else if (parserState.parentState == State.ELEMENT_CDATA) parserState.element_cdata!!.append(chr)
					} else {
						parserState.state = State.ERROR
						parserState.errorMessage = "Invalid XML entity"
					}
				}

				State.OTHER_XML -> {
					if (chr == CLOSE_BRACKET) {
						parserState.state = State.START
						handler.otherXML(parserState.element_cdata!!.toString())
						parserState.element_cdata = null

						//continue@LOOP
						continue@LOOP
					}    // end of if (chr == CLOSE_BRACKET)

					if (parserState.element_cdata == null) {
						parserState.element_cdata = StringBuilder(100)
					}    // end of if (parser_state.element_cdata == null) else

					parserState.element_cdata!!.append(chr)

					if (parserState.element_cdata!!.length > maxCdataSize) {
						parserState.state = State.ERROR
						parserState.errorMessage =
							"Max cdata size exceeded: " + maxCdataSize + "\nreceived: " + parserState.element_cdata!!.toString()
					}
				}

				State.ERROR -> {
					handler.error(parserState.errorMessage!!)

					return
				}

				// break;
				else -> throw RuntimeException("Unknown SimpleParser state: " + parserState.state)
			}// Skip everything up to open bracket
			// do nothing, skip white chars
			// Skip white characters and actually everything except quotes
			// end of switch (state)
		}      // end of for ()

		handler.saveParserState(parserState)
	}

	private fun toStringArray(src: Array<StringBuilder?>?): Array<String?>? {
		if (src == null) return null
		val res = arrayOfNulls<String>(src.size)
		for (i: Int in src.indices) res[i] = when {
			src[i] == null -> null
			else -> src[i].toString()
		}
		return res
	}

	private fun resizeArray(src: Array<StringBuilder?>?, size: Int): Array<StringBuilder?> = src!!.copyOf(size)

	internal enum class EntityType {

		UNKNOWN,
		NAMED,
		CODEPOINT,
		CODEPOINT_DEC,
		CODEPOINT_HEX
	}

	internal enum class State {

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

	internal class ParserState {

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

		//		private val QUOTES = charArrayOf(SINGLE_QUOTE, DOUBLE_QUOTE).sortedArray()
		private val WHITE_CHARS = charArrayOf(SPACE, LF, CR, TAB).sortedArray()

		//		private val END_NAME_CHARS = charArrayOf(CLOSE_BRACKET, SLASH, SPACE, TAB, LF, CR).sortedArray()
		private val ERR_NAME_CHARS = charArrayOf(OPEN_BRACKET, QUESTION_MARK, AMP).sortedArray()

		//		private val IGNORE_CHARS = charArrayOf('\u0000').sortedArray()
		private val ALLOWED_CHARS_LOW = BooleanArray(0x20)

		init {
			ALLOWED_CHARS_LOW[0x09] = true
			ALLOWED_CHARS_LOW[0x0A] = true
			ALLOWED_CHARS_LOW[0x0D] = true
		}
	}
}    // SimpleParser


