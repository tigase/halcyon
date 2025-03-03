package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.exceptions.HalcyonException

open class OMEMOException(val condition: OMEMOErrorCondition, message: String? = condition.message(), cause: Throwable? = null) : HalcyonException(message, cause) {
    class NoEncryptedElement(message: String? = "No enc element") : OMEMOException(OMEMOErrorCondition.NoEncryptedElement, message)
    class NoSidAttribute(message: String? = "No sid attribute") : OMEMOException(OMEMOErrorCondition.NoSidAttribute, message)
    class NoIV(message: String? = "No IV element") : OMEMOException(OMEMOErrorCondition.NoIvElement, message)
    class DeviceKeyNotFoundException(message: String? = null) : OMEMOException(OMEMOErrorCondition.DeviceKeyNotFound, message)
    class InvalidKeyLengthException(message: String? = null) : OMEMOException(OMEMOErrorCondition.InvalidKeyLength, message)
}