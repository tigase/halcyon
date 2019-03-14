package tigase.halcyon.core.exceptions

open class HalcyonException : Exception {
	constructor() : super()
	constructor(message: String?) : super(message)
	constructor(message: String?, cause: Throwable?) : super(message, cause)
	constructor(cause: Throwable?) : super(cause)
}