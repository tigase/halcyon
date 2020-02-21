/*
 * Tigase Halcyon XMPP Library
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
package tigase.halcyon.samples

import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.TickEvent
import tigase.halcyon.core.connector.ReceivedXMLElementEvent
import tigase.halcyon.core.connector.SentXMLElementEvent
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.requests.IQResult
import tigase.halcyon.core.requests.StanzaResult
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.modules.PingModule
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
import tigase.halcyon.core.xmpp.stanzas.message
import tigase.halcyon.core.xmpp.toBareJID
import tigase.halcyon.core.xmpp.toJID
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger

fun main(args: Array<String>) {
	val handler = ConsoleHandler()
	handler.level = Level.ALL
	Logger.getAnonymousLogger().useParentHandlers
	Logger.getLogger("tigase").addHandler(handler)
	Logger.getLogger("tigase.halcyon").level = Level.ALL
	Logger.getLogger("tigase.halcyon.core.xml").level = Level.WARNING
	Logger.getLogger("tigase.halcyon.core.eventbus").level = Level.WARNING

	val halcyon = Halcyon()

	val module = halcyon.getModule<PingModule>(PingModule.TYPE)

	halcyon.eventBus.register<Event> { t -> if (t !is TickEvent) println("EVENT: $t") }
	halcyon.eventBus.register<tigase.halcyon.core.connector.ConnectorStateChangeEvent>(
		tigase.halcyon.core.connector.ConnectorStateChangeEvent.TYPE
	) { connectorStateChangeEvent ->
		println("CONNECTOR STATE: ${connectorStateChangeEvent.oldState}->${connectorStateChangeEvent.newState}")
	}
	halcyon.eventBus.register<tigase.halcyon.core.HalcyonStateChangeEvent>(tigase.halcyon.core.HalcyonStateChangeEvent.TYPE) { stateChangeEvent ->
		println("Halcyon STATE: ${stateChangeEvent.oldState}->${stateChangeEvent.newState}")
	}
	halcyon.eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) { event ->
		println(" <<< ${event.element.getAsString()}")
	}
	halcyon.eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) { event ->
		println(" >>> ${event.element.getAsString()}")
	}

	halcyon.configuration.run {
		setJID(args[0].toBareJID())
		setPassword(args[1])
	}

//	halcyon.configurator.userJID = BareJID.parse(args[0])
//	halcyon.configurator.userPassword = args[1]

	halcyon.connect()

	loop@ while (true) {
		val line = readLine() ?: break

		when (line) {
//			"break" -> {
//				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
//				val req = pingModule.ping("jajcus.net".toJID()).response { resp ->
//					when (resp) {
//						is IQResult.Success -> println("PONG ${resp.request.stanza.to}: time=${resp.value}")
//						is IQResult.Error -> println("PONG ${resp.request.stanza.to}: error=${resp.error}")
//					}
//				}.send()
//				(halcyon.connector as SocketConnector).socket.close()
//			}
			"x" -> {
				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
				val req = pingModule.ping().send()
				val result = req.getResultWait()
				println("!!!!!!!!!!!!!!!!!!!!!!$result")
			}
			"z" -> {
				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
				val req = pingModule.ping("bmalkow@malkowscy.net".toJID()).send()
				val result = req.getResultWait()
				println("!!!!!!!!!!!!!!!!!!!!!!$result")
			}
			"y" -> {
				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
				val req = pingModule.ping().response { resp ->
					when (resp) {
						is IQResult.Success -> println("PING ${resp.request.stanza.to}: time=${resp.get()}")
						is IQResult.Error -> println("PING ${resp.request.stanza.to}: error=${resp.error}")
					}
				}.send()
			}
			"y1" -> {
				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
				val req = pingModule.ping().send()
				while (!req.isCompleted) print(".")
				val result = req.getResult()
				println("!+$result")
			}
			"m" -> {
				val req = halcyon.writeDirectly(message {
					attribute("to", "bmalkow@malkowscy.net")
					attribute("type", "chat")
					"body"{
						+"Hi!"
					}
				})
			}
			"quit" -> break@loop
			"r" -> halcyon.modules.getModule<StreamManagementModule>(StreamManagementModule.TYPE).request()
			"a" -> halcyon.modules.getModule<StreamManagementModule>(StreamManagementModule.TYPE).sendAck(true)
			"resume" -> halcyon.modules.getModule<StreamManagementModule>(StreamManagementModule.TYPE).resume()
			"p" -> {
				halcyon.request.message { to = "a@b.c".toJID() }.result { result ->
					when (result) {
						is StanzaResult.Sent -> println("Stanza ${result.request} sent")
						is StanzaResult.NotSent -> println("Stanza ${result.request} IS NOT SENT")
					}
				}.send()
			}
			"ping" -> {
				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
				pingModule.ping().response { result ->
					when (result) {
						is IQResult.Success -> println("Pong: " + result.get()!!.time + " ms")
						is IQResult.Error -> println("Pong error: ${result.error}")
					}
				}.send()

//				request.response { request, element, result ->
//					when (result) {
//						is Request.Result.Success -> {
//							result.result
//							val t = (System.nanoTime() - start) / 1000_000.0;
//							println("Pong in $t ms;   delivered=${request.isSet(StreamManagementModule.Delivered)}")
//						}
//						is Request.Result.Error -> {
//							println("Pong error: ${result.errorCondition}")
//						}
//						is Request.Result.Timeout -> println("Pong timeout")
//					}
//				}
			}
		}
	}
	halcyon.disconnect()
	println(".")
}

