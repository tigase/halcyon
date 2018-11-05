package org.tigase.jaxmpp.core.eventbus

import org.junit.Assert
import org.junit.Test
import org.tigase.jaxmpp.core.SessionObject
import java.util.concurrent.ConcurrentLinkedQueue

class EventBusMultiThreadTest {

	private val eventBus = object : AbstractEventBus(SessionObject()) {

	}

	private var working: Boolean = false

	@Test
	@Throws(Exception::class)
	fun testMultiThread() {
		val result0 = ConcurrentLinkedQueue<String>()
		val result1 = ConcurrentLinkedQueue<String>()
		val result2 = ConcurrentLinkedQueue<String>()

		eventBus.register<TestEvent>(TestEvent.TYPE) { sessionObject, t -> }

		eventBus.register<TestEvent>(AbstractEventBus.ALL_EVENTS) { sessionObject, event ->
			try {
				result0.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		eventBus.register<TestEvent>(TestEvent.TYPE) { sessionObject, event ->
			try {
				result1.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		eventBus.register<TestEvent>(TestEvent.TYPE) { sessionObject, event ->
			try {
				result2.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}

		val threads = mutableListOf<Thread>()
		val ttt = object : EventHandler<TestEvent> {
			@Override
			override fun onEvent(sessionObject: SessionObject, event: TestEvent) {

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

	internal class TestEvent(val value: String?) : Event(TYPE) {
		companion object {

			val TYPE = "test:event"
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

		private val EVENTS = 1000
		private val THREADS = 1000
	}

}
