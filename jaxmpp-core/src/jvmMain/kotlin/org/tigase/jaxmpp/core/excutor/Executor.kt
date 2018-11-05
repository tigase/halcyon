package org.tigase.jaxmpp.core.excutor

import java.util.concurrent.Executors

actual class Executor {

	private val ex = Executors.newSingleThreadExecutor()

	actual fun execute(runnable: () -> Unit) {
		ex.run { runnable.invoke() }
	}

}