package tigase.halcyon.core.logger

import java.util.logging.LogRecord

@Suppress("NOTHING_TO_INLINE")
actual class Logger actual constructor(name: String) {

	private val log = java.util.logging.Logger.getLogger(name)

	private inline fun cnv(level: tigase.halcyon.core.logger.Level): java.util.logging.Level = when (level) {
		tigase.halcyon.core.logger.Level.OFF -> java.util.logging.Level.OFF
		tigase.halcyon.core.logger.Level.SEVERE -> java.util.logging.Level.SEVERE
		tigase.halcyon.core.logger.Level.WARNING -> java.util.logging.Level.WARNING
		tigase.halcyon.core.logger.Level.INFO -> java.util.logging.Level.INFO
		tigase.halcyon.core.logger.Level.CONFIG -> java.util.logging.Level.CONFIG
		tigase.halcyon.core.logger.Level.FINE -> java.util.logging.Level.FINE
		tigase.halcyon.core.logger.Level.FINER -> java.util.logging.Level.FINER
		tigase.halcyon.core.logger.Level.FINEST -> java.util.logging.Level.FINEST
		tigase.halcyon.core.logger.Level.ALL -> java.util.logging.Level.ALL
	}

	actual fun isLoggable(level: tigase.halcyon.core.logger.Level): Boolean {
		return log.isLoggable(cnv(level))
	}

	private inline fun doLog(level: tigase.halcyon.core.logger.Level, msg: String, caught: Throwable?) {
		val lr = LogRecord(cnv(level), msg)
		if (caught != null) lr.thrown = caught

		fillCaller(lr)

		log.log(lr)
	}

	private fun fillCaller(lr: LogRecord) {
		val trace = Throwable()
		val list = trace.stackTrace

		list.find { stackTraceElement ->
			!stackTraceElement.className.startsWith(
				"tigase.halcyon.core.logger."
			)
		}.let { stackTraceElement ->
			if (stackTraceElement != null) {
				lr.sourceClassName = stackTraceElement.className
				lr.sourceMethodName = stackTraceElement.methodName
			}
		}
	}

	actual fun log(level: tigase.halcyon.core.logger.Level, msg: String) {
		doLog(level, msg, null)
	}

	actual fun log(level: tigase.halcyon.core.logger.Level, msg: String, caught: Throwable) {
		doLog(level, msg, caught)
	}

	actual fun fine(msg: String) {
		doLog(tigase.halcyon.core.logger.Level.FINE, msg, null)
	}

	actual fun finer(msg: String) {
		doLog(tigase.halcyon.core.logger.Level.FINER, msg, null)
	}

	actual fun finest(msg: String) {
		doLog(tigase.halcyon.core.logger.Level.FINEST, msg, null)
	}

	actual fun config(msg: String) {
		doLog(tigase.halcyon.core.logger.Level.CONFIG, msg, null)
	}

	actual fun info(msg: String) {
		doLog(tigase.halcyon.core.logger.Level.INFO, msg, null)
	}

	actual fun warning(msg: String) {
		doLog(tigase.halcyon.core.logger.Level.WARNING, msg, null)
	}

	actual fun severe(msg: String) {
		doLog(tigase.halcyon.core.logger.Level.SEVERE, msg, null)
	}

}