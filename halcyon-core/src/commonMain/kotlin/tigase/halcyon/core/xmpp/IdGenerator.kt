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
package tigase.halcyon.core.xmpp

import kotlin.math.absoluteValue
import kotlinx.datetime.Clock

class IdGenerator {

    private val ALPHABET: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    private var buffer = IntArray(16, init = { 0 })

    private val seed = createSeed()

    private fun createSeed(): IntArray {
        var t = Clock.System.now()
            .toEpochMilliseconds()
        var result = IntArray(0)
        while (t > 0) {
            val c = t % ALPHABET.length
            t -= c
            t /= ALPHABET.length
            result += arrayListOf(c.toInt())
        }
        return result
    }

    private var counter: Long = 0

    private fun increment() {
        for (ix in buffer.indices) {
            buffer[ix] = buffer[ix] + 1
            if (buffer[ix] < ALPHABET.length) {
                break
            }
            buffer[ix] = 0
            if (ix == buffer.size - 1) {
                buffer = IntArray(buffer.size + 1, init = { 0 })
                break
            }
        }
    }

    /**
     * Lehmer generator parameters uses the prime modulus 2^32−5:
     */
    private fun f(state: Long): Long = (state * 279470273) % 4294967291

    private fun hash(): IntArray {
        counter += 1

// 		val result =  buffer.copyOf(buffer.size + 1)

        val result = seed + buffer + IntArray(1, init = { 0 })

        val iv = f(counter)
        result[result.size - 1] = (iv % ALPHABET.length).absoluteValue.toInt()

        var prng: Long = iv

        for (ix in result.indices) {
            prng = f(prng)
            val c: Int = (prng % ALPHABET.length).toInt()
            var a = result[ix]
            a = (a + c).absoluteValue % ALPHABET.length
            result[ix] = a
            prng = prng xor a.toLong()
        }
        return result
    }

    fun nextId(): String {
        var result = ""
        for (a in hash()) {
            result += ALPHABET[a]
        }
        increment()
        return result
    }
}

private val generator by lazy { IdGenerator() }

fun nextUID(): String = generator.nextId()
fun nextUIDLongs(): String = generator.nextId() + generator.nextId()
