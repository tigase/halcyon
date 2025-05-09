package tigase.halcyon.core.xmpp.modules.omemo

expect class SignalProtocolAddress(name: String, deviceId: Int) {
    fun getName(): String
    fun getDeviceId(): Int
}
