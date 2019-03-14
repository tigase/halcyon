package tigase.halcyon.core.logger

actual class Logger actual constructor(name: String) {

	var level: tigase.halcyon.core.logger.Level = tigase.halcyon.core.logger.Level.INFO

	actual inline fun isLoggable(level: tigase.halcyon.core.logger.Level): Boolean = this.level.value >= level.value

	actual inline fun log(level: tigase.halcyon.core.logger.Level, msg: String) {
		if (isLoggable(level)) when (level) {
			tigase.halcyon.core.logger.Level.SEVERE -> console.error(msg)
			tigase.halcyon.core.logger.Level.WARNING -> console.warn(msg)
			tigase.halcyon.core.logger.Level.INFO -> console.info(msg)
			tigase.halcyon.core.logger.Level.CONFIG -> console.info(msg)
			tigase.halcyon.core.logger.Level.FINE, tigase.halcyon.core.logger.Level.FINER, tigase.halcyon.core.logger.Level.FINEST -> console.log(
				msg
			)
			else -> {
			}
		}
	}

	actual fun log(level: tigase.halcyon.core.logger.Level, msg: String, caught: Throwable) {
		log(level, msg + '\n' + caught.toString())
	}

	actual fun fine(msg: String) {
		log(tigase.halcyon.core.logger.Level.FINE, msg)
	}

	actual fun finer(msg: String) {
		log(tigase.halcyon.core.logger.Level.FINER, msg)
	}

	actual fun finest(msg: String) {
		log(tigase.halcyon.core.logger.Level.FINEST, msg)
	}

	actual fun config(msg: String) {
		log(tigase.halcyon.core.logger.Level.CONFIG, msg)
	}

	actual fun info(msg: String) {
		log(tigase.halcyon.core.logger.Level.INFO, msg)
	}

	actual fun warning(msg: String) {
		log(tigase.halcyon.core.logger.Level.WARNING, msg)
	}

	actual fun severe(msg: String) {
		log(tigase.halcyon.core.logger.Level.SEVERE, msg)
	}

}