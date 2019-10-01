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
import tigase.halcyon.core.requests.Result
import tigase.halcyon.core.xml.stanza
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.modules.PingModule
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule
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

	halcyon.eventBus.register<Event> { sessionObject, t -> if (t !is TickEvent) println("EVENT: $t") }
	halcyon.eventBus.register<tigase.halcyon.core.connector.ConnectorStateChangeEvent>(
		tigase.halcyon.core.connector.ConnectorStateChangeEvent.TYPE
	) { _, connectorStateChangeEvent ->
		println("CONNECTOR STATE: ${connectorStateChangeEvent.oldState}->${connectorStateChangeEvent.newState}")
	}
	halcyon.eventBus.register<tigase.halcyon.core.HalcyonStateChangeEvent>(tigase.halcyon.core.HalcyonStateChangeEvent.TYPE) { _, stateChangeEvent ->
		println("Halcyon STATE: ${stateChangeEvent.oldState}->${stateChangeEvent.newState}")
	}
	halcyon.eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) { _, event ->
		println(" <<< ${event.element.getAsString()}")
	}
	halcyon.eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) { _, event ->
		println(" >>> ${event.element.getAsString()}")
	}

	halcyon.configurator.userJID = BareJID.parse(args[0])
	halcyon.configurator.userPassword = args[1]

	halcyon.connect()

	loop@ while (true) {
		val line = readLine() ?: break

		when (line) {
			"x" -> {
				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
				val req = pingModule.ping()
				val result = req.getResultWait()
				println("!+$result")
			}
			"z" -> {
				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
				val req = pingModule.ping("bmalkow@malkowscy.net".toJID())
				val result = req.getResultWait()
				println("!+$result")
			}
			"y" -> {
				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
				val req = pingModule.ping()
				val result = req.getResult()
				println("!+$result")
			}
			"y1" -> {
				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
				val req = pingModule.ping()
				while (!req.completed) print(".")
				val result = req.getResult()
				println("!+$result")
			}
			"m" -> {
				val req = halcyon.write(stanza("message") {
					attribute("to", "bmalkow@malkowscy.net")
					attribute("type", "chat")
					"body"{
						+"Hi!"
					}
				})
				println("1." + req.completed)
				println("2." + req.getResult())
			}
			"?" -> {

			}
			"quit" -> break@loop
			"r" -> halcyon.modules.getModule<StreamManagementModule>(StreamManagementModule.TYPE).request()
			"resume" -> halcyon.modules.getModule<StreamManagementModule>(StreamManagementModule.TYPE).resume()
			"ping" -> {
				val pingModule = halcyon.modules.getModule<PingModule>(PingModule.TYPE)
				val request = pingModule.ping()
				request.response { request, element, result ->
					when (result) {
						is Result.Success -> println("Pong: " + result.get()!!.time + " ms")
						is Result.Error -> println("Pong error: ${result.error}")
						is Result.Timeout -> println("Pong timeout")
					}
				}

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

