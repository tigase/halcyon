package tigase.halcyon.core.utils

import platform.Foundation.NSRecursiveLock

actual class Lock {

    private val lock = NSRecursiveLock()

    actual fun <T> withLock(fn: () -> T): T {
        try {
            lock.lock()
            return fn()
        } finally {
            lock.unlock()
        }
    }
}
