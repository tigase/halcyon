package tigase.halcyon.core

actual fun currentTimestamp(): Long {
	return kotlin.js.Date.now().toLong()
}