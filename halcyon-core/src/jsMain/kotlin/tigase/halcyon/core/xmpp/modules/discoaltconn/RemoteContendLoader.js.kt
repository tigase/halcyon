package tigase.halcyon.core.xmpp.modules.discoaltconn

import org.w3c.xhr.XMLHttpRequest

actual fun loadRemoteContent(url: String, callback: (String) -> Unit) {
    val xhr = XMLHttpRequest()
    xhr.onreadystatechange = { event ->
        if (xhr.readyState == XMLHttpRequest.DONE) {
            callback(xhr.responseText)
        }
    }
    xhr.open("GET", url, true)
    xhr.send("")
}
