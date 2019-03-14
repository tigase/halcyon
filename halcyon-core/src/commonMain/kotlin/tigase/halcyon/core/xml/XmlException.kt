package tigase.halcyon.core.xml

class XmlException : tigase.halcyon.core.exceptions.HalcyonException {
	constructor() : super()
	constructor(message: String?) : super(message)
	constructor(message: String?, cause: Throwable?) : super(message, cause)
	constructor(cause: Throwable?) : super(cause)
}