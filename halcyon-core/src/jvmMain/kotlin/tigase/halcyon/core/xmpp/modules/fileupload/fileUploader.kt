package tigase.halcyon.core.xmpp.modules.fileupload

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

fun uploadFile(input: InputStream, slot: Slot): Int {
	val connection = (URL(slot.putUrl).openConnection()) as HttpURLConnection
	connection.doOutput = true
	connection.requestMethod = "PUT"
	connection.setRequestProperty("Content-Length", slot.contentLength.toString())
	connection.setRequestProperty("Content-Type", slot.contentType)

	slot.headers.forEach { (name, value) ->
		connection.setRequestProperty(name, value)
	}


	connection.outputStream.use { output ->
		val buf = ByteArray(8192)
		var length: Int
		while (input.read(buf).also { length = it } != -1) {
			output.write(buf, 0, length)
		}
		output.flush()
	}

	val responseCode: Int = connection.responseCode

	return responseCode
}

fun uploadFile(file: File, slot: Slot): Int = uploadFile(file.inputStream(), slot)
fun uploadFile(file: ByteArray, slot: Slot): Int = uploadFile(file.inputStream(), slot)
