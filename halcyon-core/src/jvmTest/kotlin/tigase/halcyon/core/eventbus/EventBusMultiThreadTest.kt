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
package tigase.halcyon.core.eventbus

import org.junit.Assert
import org.junit.Test
import tigase.halcyon.core.SessionObject
import java.util.concurrent.ConcurrentLinkedQueue

class EventBusMultiThreadTest {

	val sessionObject = SessionObject()
	val eventBus = EventBus(sessionObject)

	init {
		sessionObject.eventBus = eventBus
	}

	private var working: Boolean = false

	@Test
	@Throws(Exception::class)
	fun testMultiThread() {
		val result0 = ConcurrentLinkedQueue<String>()
		val result1 = ConcurrentLinkedQueue<String>()
		val result2 = ConcurrentLinkedQueue<String>()

		eventBus.register<TestEvent>(TestEvent.TYPE) { _, _ -> }

		eventBus.register<TestEvent>(tigase.halcyon.core.eventbus.AbstractEventBus.ALL_EVENTS) { _, event ->
			try {
				result0.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		eventBus.register<TestEvent>(TestEvent.TYPE) { _, event ->
			try {
				result1.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		eventBus.register<TestEvent>(TestEvent.TYPE) { _, event ->
			try {
				result2.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}

		val threads = mutableListOf<Thread>()
		val ttt = object : tigase.halcyon.core.eventbus.EventHandler<TestEvent> {
			@Override
			override fun onEvent(sessionObject: tigase.halcyon.core.SessionObject, event: TestEvent) {

			}
		}
		val x = object : Thread() {
			@Override
			override fun run() {
				while (working) {
					eventBus.register(TestEvent.TYPE, ttt)
					eventBus.unregister(TestEvent.TYPE, ttt)
				}
				System.out.println("Stop")
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



		while (threads.stream().filter { t -> t.isAlive }.count() > 0) {
			Thread.sleep(510)
		}
		working = false

		Assert.assertEquals(THREADS * EVENTS, result0.size)
		Assert.assertEquals(THREADS * EVENTS, result1.size)
		Assert.assertEquals(THREADS * EVENTS, result2.size)
	}

	internal class TestEvent(val value: String?) : tigase.halcyon.core.eventbus.Event(TYPE) {
		companion object {

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
				Assert.fail(prefix + " :: " + e.message)
			}

		}
	}

	companion object {
		private const val EVENTS = 1000
		private const val THREADS = 1000
	}

}
