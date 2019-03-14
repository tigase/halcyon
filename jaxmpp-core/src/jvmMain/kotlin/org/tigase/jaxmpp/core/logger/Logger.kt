package org.tigase.jaxmpp.core.logger

import java.util.logging.LogRecord

@Suppress("NOTHING_TO_INLINE")
actual class Logger actual constructor(name: String) {

	private val log = java.util.logging.Logger.getLogger(name)

	private inline fun cnv(level: Level): java.util.logging.Level = when (level) {
		Level.OFF -> java.util.logging.Level.OFF
		Level.SEVERE -> java.util.logging.Level.SEVERE
		Level.WARNING -> java.util.logging.Level.WARNING
		Level.INFO -> java.util.logging.Level.INFO
		Level.CONFIG -> java.util.logging.Level.CONFIG
		Level.FINE -> java.util.logging.Level.FINE
		Level.FINER -> java.util.logging.Level.FINER
		Level.FINEST -> java.util.logging.Level.FINEST
		Level.ALL -> java.util.logging.Level.ALL
	}

	actual fun isLoggable(level: Level): Boolean {
		return log.isLoggable(cnv(level))
	}

	private inline fun doLog(level: Level, msg: String, caught: Throwable?) {
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
				"org.tigase.jaxmpp.core.logger."
			)
		}.let { stackTraceElement ->
			if (stackTraceElement != null) {
				lr.sourceClassName = stackTraceElement.className
				lr.sourceMethodName = stackTraceElement.methodName
			}
		}
	}

	actual fun log(level: Level, msg: String) {
		doLog(level, msg, null)
	}

	actual fun log(level: Level, msg: String, caught: Throwable) {
		doLog(level, msg, caught)
	}

	actual fun fine(msg: String) {
		doLog(Level.FINE, msg, null)
	}

	actual fun finer(msg: String) {
		doLog(Level.FINER, msg, null)
	}

	actual fun finest(msg: String) {
		doLog(Level.FINEST, msg, null)
	}

	actual fun config(msg: String) {
		doLog(Level.CONFIG, msg, null)
	}

	actual fun info(msg: String) {
		doLog(Level.INFO, msg, null)
	}

	actual fun warning(msg: String) {
		doLog(Level.WARNING, msg, null)
	}

	actual fun severe(msg: String) {
		doLog(Level.SEVERE, msg, null)
	}

}