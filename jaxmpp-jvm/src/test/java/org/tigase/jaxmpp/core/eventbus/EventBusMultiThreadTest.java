package org.tigase.jaxmpp.core.eventbus;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.tigase.jaxmpp.core.SessionObject;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EventBusMultiThreadTest {

	private static int EVENTS = 1000;
	private static int THREADS = 1000;
	private final AbstractEventBus eventBus = new AbstractEventBus(new SessionObject()) {
	};

	private Boolean working;

	@Test
	public void testMultiThread() throws Exception {
		final ConcurrentLinkedQueue<String> result0 = new ConcurrentLinkedQueue<String>();
		final ConcurrentLinkedQueue<String> result1 = new ConcurrentLinkedQueue<String>();
		final ConcurrentLinkedQueue<String> result2 = new ConcurrentLinkedQueue<String>();

		eventBus.register(AbstractEventBus.ALL_EVENTS, (EventHandler<TestEvent>) (sessionObject, event) -> {
			try {
				result0.add(event.value);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		eventBus.register(TestEvent.TYPE, (EventHandler<TestEvent>) (sessionObject, event) -> {
			try {
				result1.add(event.value);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		eventBus.register(TestEvent.TYPE, (EventHandler<TestEvent>) (sessionObject, event) -> {
			try {
				result2.add(event.value);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		final ArrayList<Thread> threads = new ArrayList<>();
		final EventHandler<TestEvent> ttt = new EventHandler<TestEvent>() {
			@Override
			public void onEvent(SessionObject sessionObject, @NotNull TestEvent event) {

			}
		};
		Thread x = (new Thread() {
			@Override
			public void run() {
				while (working) {
					eventBus.register(TestEvent.TYPE, ttt);
					eventBus.unregister(TestEvent.TYPE, ttt);
				}
				System.out.println("Stop");
			}
		});

		working = true;
		x.start();

		for (int i = 0; i < THREADS; i++) {
			Thread t = (new Thread(new Worker("t:" + i)));
			t.setName("t:" + i);
			threads.add(t);
			t.start();
		}

		while (threads.stream().filter(Thread::isAlive).count() > 0) {
			Thread.sleep(510);
		}
		working = false;

		Assert.assertEquals(THREADS * EVENTS, result0.size());
		Assert.assertEquals(THREADS * EVENTS, result1.size());
		Assert.assertEquals(THREADS * EVENTS, result2.size());
	}

	static class TestEvent
			extends Event {

		public final static String TYPE = "test:event";

		final String value;

		public TestEvent(String value) {
			super(TYPE);
			this.value = value;
		}

	}

	private class Worker
			implements Runnable {

		private final String prefix;

		private Worker(String prefix) {
			this.prefix = prefix;
		}

		@Override
		public void run() {
			try {
				for (int i = 0; i < EVENTS; i++) {
					eventBus.fire(new TestEvent(prefix + "_" + i));
				}
			} catch (Exception e) {
				e.printStackTrace();
				Assert.fail(prefix + " :: " + e.getMessage());
			}
		}
	}

}
