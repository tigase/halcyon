package tigase.halcyon.core.excutor

actual class Executor actual constructor() {
	actual fun execute(runnable: () -> Unit) {
		runnable.invoke()
	}
}