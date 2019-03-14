package tigase.halcyon.core.logger

expect class Logger(name: String) {

	fun isLoggable(level: tigase.halcyon.core.logger.Level): Boolean

	fun log(level: tigase.halcyon.core.logger.Level, msg: String)
	fun log(level: tigase.halcyon.core.logger.Level, msg: String, caught: Throwable)

	fun fine(msg: String)
	fun finer(msg: String)
	fun finest(msg: String)

	fun config(msg: String)
	fun info(msg: String)
	fun warning(msg: String)
	fun severe(msg: String)

}