/*
 * halcyon-core
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core.xmpp.modules.commands

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.State
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xml.parser.parseXML
import tigase.halcyon.core.xmpp.SessionController
import tigase.halcyon.core.xmpp.forms.FieldType
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.iq
import tigase.halcyon.core.xmpp.toJID
import kotlin.test.*

class MockConnector(halcyon: AbstractHalcyon, val sentElements: MutableList<Element>) : AbstractConnector(halcyon) {

	override fun createSessionController(): SessionController {
		return object : SessionController {
			override fun start() {
			}

			override fun stop() {
			}
		}
	}

	override fun send(data: CharSequence) {
		try {
			val pr = parseXML(data.toString())
			pr.element?.let {
				sentElements.add(it)
			}
		} catch (ignore: Throwable) {
		}
	}

	override fun start() {
		state = State.Connected
	}

	override fun stop() {
		state = State.Disconnected
	}
}

fun assertContains(expected: Element, actual: Element, message: String? = null) {
	fun check(expected: Element, actual: Element): Boolean {
		if (expected.name != actual.name) return false
		if (expected.value != null && expected.value != actual.value) return false
		if (!expected.attributes.filter { it.key != "id" }
				.all { e -> actual.attributes[e.key] == e.value }) return false
		if (!expected.children.all { e ->
				actual.children.any { a -> check(e, a) }
			}) return false
		return true
	}

	fun messagePrefix(message: String?) = if (message == null) "" else "$message. "

	if (!check(expected, actual)) {
		fail(messagePrefix(message) + "Expected all of ${expected.getAsString()}, actual ${actual.getAsString()}.")
	}
}

class CommandsModuleTest {

	lateinit var halcyon: AbstractHalcyon
	lateinit var sentElements: MutableList<Element>

	@BeforeTest
	fun setUpHalcyon() {
		val tmp: MutableList<Element> = mutableListOf()
		sentElements = tmp
		halcyon = object : AbstractHalcyon() {

			override fun reconnect(immediately: Boolean) = throw NotImplementedError()
			override fun createConnector(): AbstractConnector = MockConnector(this, tmp)
		}
		halcyon.connect()
	}

	private fun processReceived(stanza: Element) {
		halcyon.eventBus.fire(ReceivedXMLElementEvent(stanza))
	}

	@Test
	fun retrieveCommandInfo() {
		val module = halcyon.getModule<CommandsModule>(CommandsModule.TYPE)

		var response: DiscoveryModule.Info? = null
		val reqId = module.retrieveCommandInfo("responder@domain".toJID(), "config").response {
			it.onSuccess { response = it }
		}.send().id

		assertContains(iq {
			type = IQType.Get
			to = "responder@domain".toJID()
			"query"{
				xmlns = "http://jabber.org/protocol/disco#info"
				attributes["node"] = "config"
			}
		}, sentElements.removeLast(), "Invalid output stanza,")

		processReceived(iq {
			type = IQType.Result
			attributes["id"] = reqId
			to = "requester@domain".toJID()
			from = "responder@domain".toJID()
			"query"{
				xmlns = "http://jabber.org/protocol/disco#info"
				attributes["node"] = "config"
				"identity"{
					attributes["name"] = "Configure Service"
					attributes["category"] = "automation"
					attributes["type"] = "command-node"
				}
				"feature"{ attributes["var"] = "http://jabber.org/protocol/commands" }
				"feature"{ attributes["var"] = "jabber:x:data" }
			}
		})

		assertNotNull(response).let { info ->
			assertEquals("config", info.node)
			assertEquals(2, info.features.size)
			assertEquals(1, info.identities.size)

			assertEquals("http://jabber.org/protocol/commands", info.features[0])
			assertEquals("jabber:x:data", info.features[1])

			assertEquals("automation", info.identities[0].category)
			assertEquals("Configure Service", info.identities[0].name)
			assertEquals("command-node", info.identities[0].type)
		}
	}

	@Test
	fun retrieveCommandsList() {
		val module = halcyon.getModule<CommandsModule>(CommandsModule.TYPE)

		var response: DiscoveryModule.Items? = null
		val reqId = module.retrieveCommandList("responder@domain".toJID()).response {
			it.onSuccess {
				response = it
			}
		}.send().id

		assertContains(iq {
			type = IQType.Get
			to = "responder@domain".toJID()
			"query"{
				xmlns = "http://jabber.org/protocol/disco#items"
				attributes["node"] = "http://jabber.org/protocol/commands"
			}
		}, sentElements.removeLast(), "Invalid output stanza,")

		processReceived(iq {
			type = IQType.Result
			attributes["id"] = reqId
			to = "requester@domain".toJID()
			from = "responder@domain".toJID()
			"query"{
				xmlns = "http://jabber.org/protocol/disco#items"
				attributes["node"] = "http://jabber.org/protocol/commands"
				"item"{
					attributes["jid"] = "responder@domain"
					attributes["node"] = "list"
					attributes["name"] = "List Service Configurations"
				}
				"item"{
					attributes["jid"] = "responder@domain"
					attributes["node"] = "config"
					attributes["name"] = "Configure Service"
				}
				"item"{
					attributes["jid"] = "responder@domain"
					attributes["node"] = "reset"
					attributes["name"] = "Reset Service Configuration"
				}
			}
		})

		assertNotNull(response).let { resp ->
			assertEquals("http://jabber.org/protocol/commands", resp.node)
			assertEquals("responder@domain", resp.jid.toString())
			assertEquals(3, resp.items.size)

			assertEquals("responder@domain", resp.items[0].jid.toString())
			assertEquals("list", resp.items[0].node)
			assertEquals("List Service Configurations", resp.items[0].name)

			assertEquals("responder@domain", resp.items[1].jid.toString())
			assertEquals("config", resp.items[1].node)
			assertEquals("Configure Service", resp.items[1].name)

			assertEquals("responder@domain", resp.items[2].jid.toString())
			assertEquals("reset", resp.items[2].node)
			assertEquals("Reset Service Configuration", resp.items[2].name)
		}

	}

	@Test
	fun simpleExecutionTest() {
		val module = halcyon.getModule<CommandsModule>(CommandsModule.TYPE)

		var response: AdHocResponse? = null
		val reqId = module.executeCommand("responder@domain".toJID(), "list").response {
			it.onSuccess { response = it }
		}.send().id

		assertContains(iq {
			type = IQType.Set
			to = "responder@domain".toJID()
			"command"{
				xmlns = "http://jabber.org/protocol/commands"
				attributes["node"] = "list"
				attributes["action"] = "execute"
			}
		}, sentElements.removeLast(), "Invalid output stanza,")

		processReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command"{
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "list:20020923T213616Z-700"
				attributes["node"] = "list"
				attributes["status"] = "completed"
				"x"{
					xmlns = "jabber:x:data"
					attributes["type"] = "result"
					"title" + { "Configure Service" }
					"instructions" + { "Please select the service to configure." }
					"field"{
						attributes["var"] = "service"
						attributes["label"] = "Service"
						attributes["type"] = "list-single"
						"option"{ "value"{ +"httpd" } }
						"option"{ "value"{ +"jabberd" } }
					}
				}

			}
		})

		assertNotNull(response).let { resp ->
			assertEquals(Status.Completed, resp.status)
			assertEquals("list", resp.node)
			assertNull(resp.defaultAction)
			assertEquals("list:20020923T213616Z-700", resp.sessionId)
			assertEquals("responder@domain", resp.jid.toString())
			assertTrue(resp.actions.isEmpty())
			assertNotNull(resp.form).let { form ->
				assertNotNull(form.getFieldByVar("service")).let {
					assertEquals(FieldType.ListSingle, it.fieldType)
					assertEquals("Service", it.fieldLabel)
				}
			}
		}
	}

	@Test
	fun multipleStagesExecutionTest() {
		val module = halcyon.getModule<CommandsModule>(CommandsModule.TYPE)

		var response: AdHocResponse? = null
		var reqId = module.executeCommand("responder@domain".toJID(), "config").response {
			it.onSuccess { response = it }
		}.send().id

		processReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command"{
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["status"] = "executing"
				"actions"{
					attributes["execute"] = "next"
					"next"{}
				}
				"x"{
					xmlns = "jabber:x:data"
					attributes["type"] = "form"
					"title" + { "Configure Service" }
					"instructions" + { "Please select the service to configure." }
					"field"{
						attributes["var"] = "service"
						attributes["label"] = "Service"
						attributes["type"] = "list-single"
						"option"{ "value"{ +"httpd" } }
						"option"{ "value"{ +"jabberd" } }
						"option"{ "value"{ +"postgresql" } }
					}
				}

			}
		})

		var frm: JabberDataForm? = null
		assertNotNull(response).let { resp ->
			assertEquals(Status.Executing, resp.status)
			assertEquals("config", resp.node)
			assertEquals(Action.Next, resp.defaultAction)
			assertEquals("config:20020923T213616Z-700", resp.sessionId)
			assertEquals("responder@domain", resp.jid.toString())
			assertEquals(1, resp.actions.size)
			assertEquals(Action.Next, resp.actions[0])
			assertNotNull(resp.form).let { form ->
				assertNotNull(form.getFieldByVar("service")).let {
					assertEquals(FieldType.ListSingle, it.fieldType)
					assertEquals("Service", it.fieldLabel)
				}
			}
			frm = resp.form!!
		}


		response = null

		frm!!.getFieldByVar("service")!!.fieldValue = "httpd"
		reqId = module.executeCommand(
			"responder@domain".toJID(), "config", frm!!.createSubmitForm(), null, "config:20020923T213616Z-700"
		).response {
			it.onSuccess { response = it }
		}.send().id


		assertContains(iq {
			type = IQType.Set
			to = "responder@domain".toJID()
			"command"{
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				"x"{
					xmlns = "jabber:x:data"
					attributes["type"] = "submit"
					"field"{
						attributes["var"] = "service"
						"value"{ +"httpd" }
					}
				}
			}
		}, sentElements.removeLast(), "Invalid output stanza,")

		processReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command"{
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["status"] = "executing"
				"actions"{
					attributes["execute"] = "complete"
					"prev"{}
					"complete"{}
				}
				"x"{
					xmlns = "jabber:x:data"
					attributes["type"] = "form"
					"title" + { "Configure Service" }
					"instructions" + { "Please select the service to configure." }
					"field"{
						attributes["var"] = "state"
						attributes["label"] = "Run State"
						attributes["type"] = "list-single"
						"value"{ +"off" }
						"option"{ attributes["label"] = "Active"; "value"{ +"off" } }
						"option"{ attributes["label"] = "Inactive"; "value"{ +"on" } }
					}
				}
			}
		})

		assertNotNull(response).let { resp ->
			assertEquals(Status.Executing, resp.status)
			assertEquals("config", resp.node)
			assertEquals(Action.Complete, resp.defaultAction)
			assertEquals("config:20020923T213616Z-700", resp.sessionId)
			assertEquals("responder@domain", resp.jid.toString())
			assertEquals(2, resp.actions.size)
			assertEquals(Action.Prev, resp.actions[0])
			assertEquals(Action.Complete, resp.actions[1])
		}

		reqId = module.executeCommand(response!!.jid, response!!.node, element("x") {
			xmlns = "jabber:x:data"
			attributes["type"] = "submit"
			"field"{
				attributes["var"] = "state"
				"value"{ +"on" }
			}
		}, null, "config:20020923T213616Z-700").response {
			it.onSuccess { response = it }
		}.send().id

		processReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command"{
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["status"] = "completed"
				"note"{
					attributes["type"] = "info"
					+"Service 'httpd' has been configured."
				}
			}
		})

		assertNotNull(response).let { resp ->
			assertEquals(Status.Completed, resp.status)
			assertEquals(1, resp.notes.size)
			assertTrue(resp.notes[0] is Note.Info)
			assertEquals("Service 'httpd' has been configured.", resp.notes[0].message)
		}
	}

	@Test
	fun cancelingTest() {
		val module = halcyon.getModule<CommandsModule>(CommandsModule.TYPE)

		var response: AdHocResponse? = null
		var reqId = module.executeCommand("responder@domain".toJID(), "config").response {
			it.onSuccess { response = it }
		}.send().id

		processReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command"{
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["status"] = "executing"
				"actions"{
					attributes["execute"] = "next"
					"next"{}
				}
				"x"{
					xmlns = "jabber:x:data"
					attributes["type"] = "form"
					"title" + { "Configure Service" }
					"instructions" + { "Please select the service to configure." }
					"field"{
						attributes["var"] = "service"
						attributes["label"] = "Service"
						attributes["type"] = "list-single"
						"option"{ "value"{ +"httpd" } }
						"option"{ "value"{ +"jabberd" } }
						"option"{ "value"{ +"postgresql" } }
					}
				}
			}
		})

		reqId =
			module.executeCommand(response!!.jid, response!!.node, null, Action.Cancel, "config:20020923T213616Z-700")
				.response {
					it.onSuccess { response = it }
				}.send().id

		assertContains(iq {
			type = IQType.Set
			to = "responder@domain".toJID()
			"command"{
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["action"] = "cancel"
			}
		}, sentElements.removeLast(), "Invalid output stanza,")


		processReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command"{
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["status"] = "canceled"
			}
		})

		assertNotNull(response).let { resp ->
			assertEquals(Status.Canceled, resp.status)
		}

	}

}