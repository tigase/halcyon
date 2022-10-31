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

import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.ElementBuilder

class XMPPDomHandler(
	val onNextElement: (Element) -> Unit,
	val onStreamStarted: (Map<String, String>) -> Unit,
	val onStreamClosed: () -> Unit,
	val onParseError: (String) -> Unit,
) : SimpleHandler {

	companion object {

		private val ELEM_STREAM_STREAM = "stream:stream"

	}

	private val log = LoggerFactory.logger("tigase.halcyon.core.xml.parser.XMPPDomHandler", false)

	private val namespaces = HashMap<String, String>()

	private var parserState: Any? = null

	private var elementBuilder: ElementBuilder? = null

	override fun endElement(name: String): Boolean {
		log.finest { "End element name: $name" }

		val tmpName = name

		if (tmpName == ELEM_STREAM_STREAM) {
			onStreamClosed.invoke()
			return true
		}

		val elemName = tmpName.substringAfter(":")

		if (elementBuilder != null && elementBuilder!!.onTop && elementBuilder!!.currentElement.name == elemName) {
			val element = elementBuilder!!.build()
			elementBuilder = null
			onNextElement.invoke(element)
			return true
		} else if (elementBuilder != null && elementBuilder!!.currentElement.name == elemName) {
			elementBuilder!!.up()
			return true
		}

		return false
	}

	override fun startElement(name: String, attrNames: Array<String?>?, attrValues: Array<String?>?) {
		log.finest {
			"""Start element name: $name
			Element attributes names: ${attrNames?.joinToString { " " }}
			Element attributes values: ${attrValues?.joinToString { " " }}	
		""".trimIndent()
		}

		// Look for 'xmlns:' declarations:
		if (attrNames != null) {
			for (i in attrNames.indices) {

				// Exit the loop as soon as we reach end of attributes set
				if (attrNames[i] == null) {
					break
				}

				if (attrNames[i].toString()
						.startsWith("xmlns:")
				) {

					// TODO should use a StringCache instead of intern() to
					// avoid potential
					// DOS by exhausting permgen
					namespaces.put(
						attrNames[i]!!.substring("xmlns:".length, attrNames[i]!!.length), attrValues!![i].toString()
					)

					log.finest { "Namespace found: ${attrValues[i].toString()}" }
				} // end of if (att_name.startsWith("xmlns:"))
			} // end of for (String att_name : attnames)
		} // end of if (attr_names != null)

		var tmpName = name

		if (tmpName == ELEM_STREAM_STREAM) {
			val attribs = HashMap<String, String>()

			if (attrNames != null) {
				for (i in attrNames.indices) {
					if (attrNames[i] != null && attrValues!![i] != null) {
						attribs[attrNames[i].toString()] = attrValues[i].toString()
					} else {
						break
					} // end of else
				} // end of for (int i = 0; i < attr_names.length; i++)
			} // end of if (attr_name != null)

			onStreamStarted(attribs)

			return
		} // end of if (tmp_name.equals(ELEM_STREAM_STREAM))

		var new_xmlns: String? = null
		var prefix: String? = null
		var tmpNamePrefix: String? = null
		val idx = tmpName.indexOf(':')

		if (idx > 0) {
			tmpNamePrefix = tmpName.substring(0, idx)

			log.finest { "Found prefixed element name, prefix: $tmpNamePrefix" }
		}

		if (tmpNamePrefix != null) {
			for (pref in namespaces.keys) {
				if (tmpNamePrefix == pref) {
					new_xmlns = namespaces.get(pref)
					tmpName = tmpName.substring(pref.length + 1, tmpName.length)
					prefix = pref

					log.finest { "new_xmlns = $new_xmlns" }
				} // end of if (tmp_name.startsWith(xmlns))
			} // end of for (String xmlns: namespaces.keys())
		}

		val attribs = mutableMapOf<String, String>()
		if (attrNames != null) {
			for (i in 0 until attrNames.size) {
				val k = attrNames[i]
				val v = attrValues!![i]
				if (k != null && v != null) attribs[k.toString()] = v.toString()
			}
		}

		if (elementBuilder != null) {
			elementBuilder!!.child(tmpName)
		} else {
			elementBuilder = ElementBuilder.create(tmpName)
		}

		if (new_xmlns != null) {
			elementBuilder!!.xmlns(new_xmlns)
			attribs.remove("xmlns:" + prefix!!)
			log.finest { "new_xmlns assigned: ${elementBuilder!!.currentElement.getAsString()}" }
		}

		elementBuilder!!.attributes(attribs)

	}

	override fun restoreParserState(): Any? = parserState

	override fun elementCData(cdata: String) {
		elementBuilder!!.value(cdata)
	}

	override fun saveParserState(state: Any?) {
		this.parserState = state
	}

	override fun otherXML(other: String) {
		log.finest { "Other XML content: $other" }
	}

	override fun error(errorMessage: String) {
		log.warning { "XML content parse error." }

		log.fine { errorMessage }

		onParseError.invoke(errorMessage)
	}
}