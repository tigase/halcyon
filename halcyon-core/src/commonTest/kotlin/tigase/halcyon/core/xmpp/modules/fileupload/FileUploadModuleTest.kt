package tigase.halcyon.core.xmpp.modules.fileupload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import tigase.DummyHalcyon
import tigase.assertContains
import tigase.halcyon.core.requests.XMPPError
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toJID

class FileUploadModuleTest {

    val halcyon = DummyHalcyon().apply {
        connect()
    }

    @Test
    fun testSlotRequestErrorFileTooLarge() {
        var failure: Throwable? = null
        val reqId = halcyon.getModule(FileUploadModule).requestSlot(
            "upload.montague.tld".toJID(),
            "très cool.jpg",
            23456,
            "image/jpeg"
        ).response {
            it.onFailure { failure = it }
        }.send().id
        assertContains(
            iq {
                type = IQType.Get
                to = "upload.montague.tld".toJID()
                "request"("urn:xmpp:http:upload:0") {
                    attributes {
                        "filename" to "très cool.jpg"
                        "size" to "23456"
                        "content-type" to "image/jpeg"
                    }
                }
            },
            halcyon.peekLastSend(),
            "Invalid output stanza,"
        )

        halcyon.addReceived(
            iq {
                id(reqId)
                type = IQType.Error
                from = "upload.montague.tld".toJID()
                to = "user@example.com".toJID()
                "error" {
                    attributes["type"] = "modify"
                    "not-acceptable" { xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas" }
                    "text" {
                        xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
                        +"File too large. The maximum file size is 20000 bytes"
                    }
                    "file-too-large" {
                        xmlns = "urn:xmpp:http:upload:0"
                        "max-file-size" {
                            +"20001"
                        }
                    }
                }
            }
        )

        assertNotNull(failure) {
            assertIs<XMPPError>(it).let {
                assertEquals(ErrorCondition.NotAcceptable, it.error)
            }
            assertIs<FileTooLargeSlotRequestException>(it).let {
                assertEquals(20001, it.maxFileSize)
            }
        }
    }

    @Test
    fun testSlotRequestErrorQuota() {
        var failure: Throwable? = null
        val reqId = halcyon.getModule(FileUploadModule).requestSlot(
            "upload.montague.tld".toJID(),
            "très cool.jpg",
            23456,
            "image/jpeg"
        ).response {
            it.onFailure { failure = it }
        }.send().id
        assertContains(
            iq {
                type = IQType.Get
                to = "upload.montague.tld".toJID()
                "request"("urn:xmpp:http:upload:0") {
                    attributes {
                        "filename" to "très cool.jpg"
                        "size" to "23456"
                        "content-type" to "image/jpeg"
                    }
                }
            },
            halcyon.peekLastSend(),
            "Invalid output stanza,"
        )

        mapOf("s" to "s")

        halcyon.addReceived(
            iq {
                id(reqId)
                type = IQType.Error
                from = "upload.montague.tld".toJID()
                to = "user@example.com".toJID()
                "error" {
                    attributes["type"] = "wait"
                    "resource-constraint" { xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas" }
                    "text" {
                        xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
                        +"Quota reached. You can only upload 5 files in 5 minutes"
                    }
                    "retry" {
                        xmlns = "urn:xmpp:http:upload:0"
                        attributes["stamp"] = "2017-12-03T23:42:05Z"
                    }
                }
            }
        )

        assertNotNull(failure) {
            assertIs<XMPPError>(it).let {
                assertEquals(ErrorCondition.ResourceConstraint, it.error)
            }
            assertIs<QuotaSlotRequestException>(it).let {
                assertNotNull(it.tryAt).let {
                    assertEquals(1512344525, it.epochSeconds)
                }
            }
        }
    }

    @Test
    fun testSlotRequestErrorForbidden() {
        var failure: Throwable? = null
        val reqId = halcyon.getModule(FileUploadModule).requestSlot(
            "upload.montague.tld".toJID(),
            "très cool.jpg",
            23456,
            "image/jpeg"
        ).response {
            it.onFailure { failure = it }
        }.send().id
        assertContains(
            iq {
                type = IQType.Get
                to = "upload.montague.tld".toJID()
                "request" {
                    xmlns = "urn:xmpp:http:upload:0"
                    attributes["filename"] = "très cool.jpg"
                    attributes["size"] = "23456"
                    attributes["content-type"] = "image/jpeg"
                }
            },
            halcyon.peekLastSend(),
            "Invalid output stanza,"
        )

        halcyon.addReceived(
            iq {
                id(reqId)
                type = IQType.Error
                from = "upload.montague.tld".toJID()
                to = "user@example.com".toJID()
                "error" {
                    attributes["type"] = "auth"
                    "forbidden" { xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas" }
                    "text" {
                        xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
                        +"Only premium members are allowed to upload files"
                    }
                }
            }
        )

        assertNotNull(failure) {
            assertIs<XMPPError>(it).let {
                assertEquals(ErrorCondition.Forbidden, it.error)
            }
        }
    }

    @Test
    fun testSlotRequestSuccess() {
        var slot: Slot? = null
        val reqId = halcyon.getModule(FileUploadModule).requestSlot(
            "upload.montague.tld".toJID(),
            "très cool.jpg",
            23456,
            "image/jpeg"
        ).response {
            it.onSuccess { slot = it }
        }.send().id
        assertContains(
            iq {
                type = IQType.Get
                to = "upload.montague.tld".toJID()
                "request" {
                    xmlns = "urn:xmpp:http:upload:0"
                    attributes {
                        "filename" to "très cool.jpg"
                        "size" to "23456"
                        "content-type" to "image/jpeg"
                    }
                }
            },
            halcyon.peekLastSend(),
            "Invalid output stanza,"
        )

        halcyon.addReceived(
            iq {
                id(reqId)
                type = IQType.Result
                from = "upload.montague.tld".toJID()
                to = "user@example.com".toJID()
                "slot" {
                    xmlns = "urn:xmpp:http:upload:0"
                    "put" {
                        attributes["url"] =
                            "https://upload.montague.tld/4a771ac1-f0b2-4a4a-9700-f2a26fa2bb67/tr%C3%A8s%20cool.jpg"
                        "header" {
                            attributes["name"] = "Authorization"
                            +"Basic Base64String=="
                        }
                        "header" {
                            attributes["name"] = "Cookie"
                            +"foo=bar; user=romeo"
                        }
                    }
                    "get" {
                        attributes["url"] =
                            "https://download.montague.tld/4a771ac1-f0b2-4a4a-9700-f2a26fa2bb67/tr%C3%A8s%20cool.jpg"
                    }
                }
            }
        )

        assertNotNull(slot) {
            assertEquals(
                "https://download.montague.tld/4a771ac1-f0b2-4a4a-9700-f2a26fa2bb67/tr%C3%A8s%20cool.jpg",
                it.getUrl
            )
            assertEquals(
                "https://upload.montague.tld/4a771ac1-f0b2-4a4a-9700-f2a26fa2bb67/tr%C3%A8s%20cool.jpg",
                it.putUrl
            )
            assertEquals(2, it.headers.size)
            assertEquals("Basic Base64String==", it.headers["Authorization"])
            assertEquals("foo=bar; user=romeo", it.headers["Cookie"])
        }
    }
}
