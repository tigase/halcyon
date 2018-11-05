/*
 * Base64.java
 *
 * Tigase XMPP Client Library
 * Copyright (C) 2006-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
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
package org.tigase.jaxmpp.core

/**
 * Base64 encoder/decoder.
 */
object Base64 {

	private val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

	private val ALPHABET_1 = IntArray(256)

	init {
		for (i in ALPHABET_1.indices) {
			ALPHABET_1[i] = -1
		}
		for (i in ALPHABET.indices) {
			ALPHABET_1[ALPHABET[i].toInt()] = i
		}
		ALPHABET_1['='.toInt()] = 0
	}

	/**
	 * Translates the specified Base64 string into a byte array.
	 *
	 * @param s the Base64 string (not null)
	 *
	 * @return the byte array (not null)
	 */
	fun decode(s: String): ByteArray {
		var separatorsCounter = 0
		val inputLen = s.length
		for (i in 0 until inputLen) {
			val c = ALPHABET_1[s[i].toInt()]
			if (c < 0 && c != '='.toInt()) {
				separatorsCounter++
			}
		}

		var deltas = 0
		var i = inputLen - 1
		while (i > 1 && ALPHABET_1[s[i].toInt()] <= 0) {
			if (s[i] == '=') {
				++deltas
			}
			--i
		}

		val outputLen = (inputLen - separatorsCounter) * 3 / 4 - deltas

		val buffer = ByteArray(outputLen)
		val mask = 0xFF
		var index = 0
		var o: Int
		o = 0
		while (o < s.length) {

			var c0 = ALPHABET_1[s[o++].toInt()]
			if (c0 == -1) {
				o = findNexIt(s, --o)
				c0 = ALPHABET_1[s[o++].toInt()]
				if (c0 == -1) {
					break
				}
			}
			var c1 = ALPHABET_1[s[o++].toInt()]
			if (c1 == -1) {
				o = findNexIt(s, --o)
				c1 = ALPHABET_1[s[o++].toInt()]
				if (c1 == -1) {
					break
				}
			}

			buffer[index++] = (c0 shl 2 or (c1 shr 4) and mask).toByte()
			if (index >= buffer.size) {
				break
			}
			var c2 = ALPHABET_1[s[o++].toInt()]
			if (c2 == -1) {
				o = findNexIt(s, --o)
				c2 = ALPHABET_1[s[o++].toInt()]
				if (c2 == -1) {
					break
				}
			}
			buffer[index++] = (c1 shl 4 or (c2 shr 2) and mask).toByte()
			if (index >= buffer.size) {
				break
			}
			var c3 = ALPHABET_1[s[o++].toInt()]
			if (c3 == -1) {
				o = findNexIt(s, --o)
				c3 = ALPHABET_1[s[o++].toInt()]
				if (c3 == -1) {
					break
				}
			}
			buffer[index++] = (c2 shl 6 or c3 and mask).toByte()
		}

		return buffer
	}

	/**
	 * Translates the specified byte array into Base64 string.
	 *
	 * @param buf the byte array (not null)
	 *
	 * @return the translated Base64 string (not null)
	 */
	fun encode(buf: CharSequence): String {
		val size = buf.length
		val outputSize = (size + 2) / 3 * 4
		val output = CharArray(outputSize)
		var a = 0
		var i = 0
		while (i < size) {
			val b0 = buf[i++].toInt()
			val b1 = if (i < size) buf[i++].toInt() else 0
			val b2 = if (i < size) buf[i++].toInt() else 0

			val mask = 0x3F
			output[a++] = ALPHABET[b0 shr 2 and mask]
			output[a++] = ALPHABET[b0 shl 4 or (b1 and 0xFF shr 4) and mask]
			output[a++] = ALPHABET[b1 shl 2 or (b2 and 0xFF shr 6) and mask]
			output[a++] = ALPHABET[b2 and mask]
		}
		when (size % 3) {
			1 -> {
				output[--a] = '='
				output[--a] = '='
			}
			2 -> output[--a] = '='
		}
		return String(output)
	}

	private fun findNexIt(s: String, i: Int): Int {
		var i = i
		val sl = s.length - 1
		var c2: Int
		if (i >= sl) {
			return i
		}
		do {
			c2 = ALPHABET_1[s[++i].toInt()]
		} while (c2 == -1 && i < sl)

		return i
	}
}