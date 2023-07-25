package tigase.halcyon.core.xmpp.modules.fileupload

import org.w3c.files.File
import org.w3c.xhr.XMLHttpRequest

fun upload(content: File, slot: Slot) {
	val xhr = XMLHttpRequest()
	xhr.open("PUT", slot.putUrl)
	slot.headers.forEach { (name, value) ->
		xhr.setRequestHeader(name, value)
	}
	xhr.send(content)
}

fun upload(content: ByteArray, slot: Slot) {
	val xhr = XMLHttpRequest()
	xhr.open("PUT", slot.putUrl)
	slot.headers.forEach { (name, value) ->
		xhr.setRequestHeader(name, value)
	}
	xhr.send(content)
}

