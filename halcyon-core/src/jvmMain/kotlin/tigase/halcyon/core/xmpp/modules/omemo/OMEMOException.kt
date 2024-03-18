package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.exceptions.HalcyonException

class OMEMOException : HalcyonException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)

}