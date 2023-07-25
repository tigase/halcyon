package tigase.halcyon.tests

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.ReflectionModuleManager
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.xmpp.modules.fileupload.FileUploadModule
import tigase.halcyon.core.xmpp.modules.fileupload.uploadFile
import tigase.halcyon.core.xmpp.toJID
import java.io.File
import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals

class FileUploadTest {

	init {
		val logger = Logger.getLogger("tigase")
		val handler: Handler = ConsoleHandler()
		handler.level = Level.INFO
		logger.addHandler(handler)
		logger.level = Level.INFO

	}

	@OptIn(ReflectionModuleManager::class)
	@Test
	fun uploadFileTest() {
		val halcyon = createHalcyon()

		halcyon.eventBus.register<Event> {
			println("EVENT: $it")
		}

		halcyon.connectAndWait()
		println("Connected!")
		assertEquals(AbstractHalcyon.State.Connected, halcyon.state, "Client should be connected to server.")


		val file = File("/Users/bmalkow/Downloads/IMG20230428164155.jpg")
		halcyon.getModule(FileUploadModule).requestSlot(
			"upload.sure.im".toJID(), "testfile.jpg", file.length(), "image/jpg"
		).response {
			println("!!!!>$it")

			uploadFile(file, it.getOrThrow())

		}.send()


		halcyon.waitForAllResponses()
		assertEquals(0, halcyon.requestsManager.getWaitingRequestsSize())
		halcyon.disconnect()
		assertEquals(AbstractHalcyon.State.Stopped, halcyon.state, "Client should be connected to server.")
	}


}