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
package tigase.halcyon.core.eventbus

import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.builder.createConfiguration
import tigase.halcyon.core.eventbus.EventBusInterface.Companion.ALL_EVENTS
import tigase.halcyon.core.xmpp.toBareJID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.*

class EventBusMultiThreadTest {

	private val halcyon = Halcyon(createConfiguration {
		account {
			userJID = "testuser@tigase.org".toBareJID()
			passwordCallback = { "testuserpassword" }
		}
	})
	val eventBus = EventBus(halcyon)

	private var working: Boolean = false

	@Test
	@Throws(Exception::class)
	fun testMultiThread() {
		val result0 = ConcurrentLinkedQueue<String>()
		val result1 = ConcurrentLinkedQueue<String>()
		val result2 = ConcurrentLinkedQueue<String>()

		eventBus.register<TestEvent>(TestEvent.TYPE) { }

		eventBus.register<TestEvent>(ALL_EVENTS) { event ->
			try {
				result0.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		eventBus.register<TestEvent>(TestEvent.TYPE) { event ->
			try {
				result1.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		eventBus.register<TestEvent>(TestEvent.TYPE) { event ->
			try {
				result2.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}

		val threads = mutableListOf<Thread>()
		val ttt = object : EventHandler<TestEvent> {
			@Override
			override fun onEvent(event: TestEvent) {

			}
		}
		val x = object : Thread() {
			@Override
			override fun run() {
				while (working) {
					eventBus.register(TestEvent.TYPE, ttt)
					eventBus.unregister(TestEvent.TYPE, ttt)
				}
				println("Stop")
			}
		}

		working = true
		x.start()

		for (i in 0 until THREADS) {
			val t = Thread(Worker("t:$i"))
			t.name = "t:$i"
			threads.add(t)
			t.start()
		}



		while (threads.stream()
				.filter { t -> t.isAlive }
				.count() > 0
		) {
			Thread.sleep(510)
		}
		working = false

		assertEquals(THREADS * EVENTS, result0.size)
		assertEquals(THREADS * EVENTS, result1.size)
		assertEquals(THREADS * EVENTS, result2.size)
	}

	internal class TestEvent(val value: String?) : Event(TYPE) { companion object {

		const val TYPE = "test:event"
	}

	}

	internal inner class Worker constructor(private val prefix: String) : Runnable {

		override fun run() {
			try {
				for (i in 0 until EVENTS) {
					eventBus.fire(TestEvent(prefix + "_" + i))
				}
			} catch (e: Exception) {
				e.printStackTrace()
				fail(prefix + " :: " + e.message)
			}

		}
	}

	companion object {

		private const val EVENTS = 1000
		private const val THREADS = 1000
	}

}
