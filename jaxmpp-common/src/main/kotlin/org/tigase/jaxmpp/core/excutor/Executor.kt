package org.tigase.jaxmpp.core.excutor

expect class Executor() {

	fun execute(runnable: () -> Unit)

}