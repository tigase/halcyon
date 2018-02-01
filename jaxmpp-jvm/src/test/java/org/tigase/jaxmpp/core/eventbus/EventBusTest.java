package org.tigase.jaxmpp.core.eventbus;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

public class EventBusTest {

	@Test
	public void testBasic() {
		final EventBus eventBus = new EventBus();
		final ArrayList<String> responses = new ArrayList<>();

		EventHandler<TestEvent> handler = new EventHandler<TestEvent>() {
			@Override
			public void onEvent(@NotNull TestEvent event) {
				responses.add(((TestEvent) event).value);
			}
		};

		eventBus.register(TestEvent.TYPE, handler);

		eventBus.fire(new TestEvent("01"));
		eventBus.fire(new TestEvent("02"));
		eventBus.fire(new TestEvent("03"));
		eventBus.fire(new TestEvent("04"));
		eventBus.fire(new TestEvent("05"));

		Assert.assertTrue(responses.contains("01"));
		Assert.assertTrue(responses.contains("02"));
		Assert.assertTrue(responses.contains("03"));
		Assert.assertTrue(responses.contains("04"));
		Assert.assertTrue(responses.contains("05"));
		Assert.assertFalse(responses.contains("06"));

		eventBus.unregister(handler);

		eventBus.fire(new TestEvent("06"));
		Assert.assertFalse(responses.contains("06"));

		eventBus.register(EventBus.ALL_EVENTS, handler);

		eventBus.fire(new TestEvent("07"));
		Assert.assertTrue(responses.contains("07"));

	}

	static class TestEvent
			extends Event {

		public final static String TYPE = "test";
		final String value;

		public TestEvent(String value) {
			super(TYPE);
			this.value = value;
		}

	}

}
