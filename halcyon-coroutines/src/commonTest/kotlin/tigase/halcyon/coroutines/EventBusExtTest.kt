package tigase.halcyon.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.builder.createConfiguration
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventBus
import tigase.halcyon.core.xmpp.toBareJID
import kotlin.test.Test
import kotlin.test.assertEquals

@DelicateCoroutinesApi
class EventBusExtTest {

	data class Test1Event(val data: String) : Event(TYPE) {

		companion object {

			const val TYPE = "Test1Event"
		}
	}

	data class Test2Event(val data: String) : Event(TYPE) {

		companion object {

			const val TYPE = "Test2Event"
		}
	}

	@ObsoleteCoroutinesApi
	@Test
	fun testObserve() {
		val eventBus = EventBus(object : AbstractHalcyon(createConfiguration {
			account {
				userJID = "test@tester.com".toBareJID()
				passwordCallback = { "test" }
				resource = "test"

			}
		}) {
			override fun reconnect(immediately: Boolean) = TODO("Not yet implemented")
			override fun createConnector(): AbstractConnector = TODO("Not yet implemented")
		})

		val o1 = mutableListOf<Event>()
		val o2 = mutableListOf<Test1Event>()
		val o3 = mutableListOf<Test2Event>()


		GlobalScope.launch(Dispatchers.Unconfined) {
			eventBus.asFlow<Test1Event>(Test1Event.TYPE)
				.collect(o2::add)
		}
		GlobalScope.launch(Dispatchers.Unconfined) {
			eventBus.asFlow<Test2Event>(Test2Event.TYPE)
				.collect(o3::add)
		}
		GlobalScope.launch(Dispatchers.Unconfined) {
			eventBus.asFlow<Event>()
				.collect(o1::add)
		}

		eventBus.fire(Test1Event("1.1"))
		eventBus.fire(Test2Event("2.1"))
		eventBus.fire(Test1Event("1.2"))
		eventBus.fire(Test2Event("2.2"))

		assertEquals(o1, listOf(Test1Event("1.1"), Test2Event("2.1"), Test1Event("1.2"), Test2Event("2.2")))
		assertEquals(o2, listOf(Test1Event("1.1"), Test1Event("1.2")))
		assertEquals(o3, listOf(Test2Event("2.1"), Test2Event("2.2")))


		eventBus.fire(Test2Event("2.3"))

		assertEquals(
			o1, listOf(
				Test1Event("1.1"), Test2Event("2.1"), Test1Event("1.2"), Test2Event("2.2"), Test2Event("2.3")
			)
		)
		assertEquals(o2, listOf(Test1Event("1.1"), Test1Event("1.2")))
		assertEquals(o3, listOf(Test2Event("2.1"), Test2Event("2.2"), Test2Event("2.3")))
	}

	@ObsoleteCoroutinesApi
	@Test
	fun testObserveFilter() {
		val eventBus = EventBus(object : AbstractHalcyon(createConfiguration {
			account {
				userJID = "test@tester.com".toBareJID()
				passwordCallback = { "test" }
				resource = "test"

			}
		}) {
			override fun reconnect(immediately: Boolean) = TODO("Not yet implemented")
			override fun createConnector(): AbstractConnector = TODO("Not yet implemented")
		})

		val o1 = mutableListOf<Event>()
		val o2 = mutableListOf<Test1Event>()
		val o3 = mutableListOf<Test2Event>()


		GlobalScope.launch(Dispatchers.Unconfined) {
			eventBus.asFlow<Event>()
				.collect(o1::add)
		}
		GlobalScope.launch(Dispatchers.Unconfined) {
			eventBus.asFlow<Event>()
				.filterIsInstance<Test1Event>()
				.collect(o2::add)
		}
		GlobalScope.launch(Dispatchers.Unconfined) {
			eventBus.asFlow<Event>()
				.filterIsInstance<Test2Event>()
				.collect(o3::add)
		}

		eventBus.fire(Test1Event("1.1"))
		eventBus.fire(Test2Event("2.1"))
		eventBus.fire(Test1Event("1.2"))
		eventBus.fire(Test2Event("2.2"))

		assertEquals(o1, listOf(Test1Event("1.1"), Test2Event("2.1"), Test1Event("1.2"), Test2Event("2.2")))
		assertEquals(o2, listOf(Test1Event("1.1"), Test1Event("1.2")))
		assertEquals(o3, listOf(Test2Event("2.1"), Test2Event("2.2")))


		eventBus.fire(Test2Event("2.3"))

		assertEquals(
			o1, listOf(
				Test1Event("1.1"), Test2Event("2.1"), Test1Event("1.2"), Test2Event("2.2"), Test2Event("2.3")
			)
		)
		assertEquals(o2, listOf(Test1Event("1.1"), Test1Event("1.2")))
		assertEquals(o3, listOf(Test2Event("2.1"), Test2Event("2.2"), Test2Event("2.3")))
	}

}