package tigase.halcyon.rx

import com.badoo.reaktive.test.base.assertNotError
import com.badoo.reaktive.test.observable.TestObservableObserver
import com.badoo.reaktive.test.observable.assertValues
import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.builder.createConfiguration
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventBus
import tigase.halcyon.core.xmpp.toBareJID
import kotlin.test.Test

class EventBusRxTest {

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
		val o1 = TestObservableObserver<Event>(false)
		val o2 = TestObservableObserver<Test1Event>(false)
		val o3 = TestObservableObserver<Test2Event>(false)

		eventBus.observe<Event>()
			.subscribe(o1)
		eventBus.observe<Test1Event>(Test1Event.TYPE)
			.subscribe(o2)
		eventBus.observe<Test2Event>(Test2Event.TYPE)
			.subscribe(o3)

		eventBus.fire(Test1Event("1.1"))
		eventBus.fire(Test2Event("2.1"))
		eventBus.fire(Test1Event("1.2"))
		eventBus.fire(Test2Event("2.2"))

		o1.assertNotError()
		o2.assertNotError()
		o3.assertNotError()
		o1.assertValues(Test1Event("1.1"), Test2Event("2.1"), Test1Event("1.2"), Test2Event("2.2"))
		o2.assertValues(Test1Event("1.1"), Test1Event("1.2"))
		o3.assertValues(Test2Event("2.1"), Test2Event("2.2"))

		o1.dispose()

		eventBus.fire(Test2Event("2.3"))

		o1.assertValues(Test1Event("1.1"), Test2Event("2.1"), Test1Event("1.2"), Test2Event("2.2"))

	}

}