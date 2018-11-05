package org.tigase.jaxmpp.core.logger

expect class Logger(name: String) {

	fun isLoggable(level: Level): Boolean

	fun log(level: Level, msg: String)
	fun log(level: Level, msg: String, caught: Exception)

	fun fine(msg: String)
	fun finer(msg: String)
	fun finest(msg: String)

	fun config(msg: String)
	fun info(msg: String)
	fun warning(msg: String)
	fun severe(msg: String)

}