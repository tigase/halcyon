package org.tigase.jaxmpp.core.logger

actual class Logger actual constructor(name: String) {

	var level: Level = Level.INFO

	actual inline fun isLoggable(level: Level): Boolean = this.level.value >= level.value

	actual inline fun log(level: Level, msg: String) {
		if (isLoggable(level)) when (level) {
			Level.SEVERE -> console.error(msg)
			Level.WARNING -> console.warn(msg)
			Level.INFO -> console.info(msg)
			Level.CONFIG -> console.info(msg)
			Level.FINE, Level.FINER, Level.FINEST -> console.log(msg)
			else -> {
			}
		}
	}

	actual fun log(level: Level, msg: String, caught: Exception) {
		log(level, msg + '\n' + caught.toString())
	}

	actual fun fine(msg: String) {
		log(Level.FINE, msg)
	}

	actual fun finer(msg: String) {
		log(Level.FINER, msg)
	}

	actual fun finest(msg: String) {
		log(Level.FINEST, msg)
	}

	actual fun config(msg: String) {
		log(Level.CONFIG, msg)
	}

	actual fun info(msg: String) {
		log(Level.INFO, msg)
	}

	actual fun warning(msg: String) {
		log(Level.WARNING, msg)
	}

	actual fun severe(msg: String) {
		log(Level.SEVERE, msg)
	}

}