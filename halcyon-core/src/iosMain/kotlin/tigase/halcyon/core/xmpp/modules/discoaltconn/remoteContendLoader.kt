package tigase.halcyon.core.xmpp.modules.discoaltconn

actual fun loadRemoteContent(url: String, callback: (String) -> Unit) {
    try {
        // FIXME: dummy result, need to be reimplemented...
        callback("")
    } catch (e: Exception) {
        e.printStackTrace()
        callback("")
    }
}