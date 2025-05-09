package tigase.halcyon.core.utils

actual class Lock {
    actual fun <T> withLock(fn: () -> T): T = fn()
}
