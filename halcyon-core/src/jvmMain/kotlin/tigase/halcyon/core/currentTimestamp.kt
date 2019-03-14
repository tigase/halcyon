package tigase.halcyon.core

actual fun currentTimestamp(): Long {
	return System.currentTimeMillis()
}