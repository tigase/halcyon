package tigase.halcyon.core.excutor

expect class Executor() {

	fun execute(runnable: () -> Unit)

}