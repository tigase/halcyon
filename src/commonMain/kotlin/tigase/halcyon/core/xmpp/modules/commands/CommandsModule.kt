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

import tigase.halcyon.core.Context
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType

enum class Status(val xmppValue: String) {

	/**
	 * The command is being executed.
	 */
	Executing("executing"),

	/**
	 * The command has completed. The command session has ended.
	 */
	Completed("completed"),

	/**
	 * The command has been canceled. The command session has ended.
	 */
	Canceled("canceled")
}

enum class Action(val xmppValue: String) {

	/**
	 * The command should be executed or continue to be executed. This is the default value.
	 */
	Execute("execute"),

	/**
	 * The command should be canceled.
	 */
	Cancel("cancel"),

	/**
	 * The command should be digress to the previous stage of execution.
	 */
	Prev("prev"),

	/**
	 * The command should progress to the next stage of execution.
	 */
	Next("next"),

	/**
	 * The command should be completed (if possible).
	 */
	Complete("complete")

}

sealed class Note(val message: String?) {

	class Info(message: String?) : Note(message)
	class Warn(message: String?) : Note(message)
	class Error(message: String?) : Note(message)

}

data class AdHocResponse(
	val jid: JID?,
	val node: String,
	val sessionId: String?,
	val status: Status,
	val form: JabberDataForm?,
	val actions: Array<Action>,
	val defaultAction: Action?,
	val notes: Array<Note>
)

class CommandsModule(override val context: Context) : XmppModule {

	companion object {

		const val XMLNS = "http://jabber.org/protocol/commands"
		const val TYPE = XMLNS
		const val NODE = XMLNS
	}

	override val type = TYPE
	override val criteria: Criteria? = null
	override val features: Array<String> = arrayOf(XMLNS)

	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.commands.CommandsModule")

	override fun initialize() {
	}

	override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

	fun retrieveCommandList(jid: JID?): RequestBuilder<DiscoveryModule.Items, IQ> {
		val disco = context.modules.get<DiscoveryModule>(DiscoveryModule.TYPE)
		return disco.items(jid, NODE)
	}

	fun retrieveCommandInfo(jid: JID?, command: String): RequestBuilder<DiscoveryModule.Info, IQ> {
		val disco = context.modules.get<DiscoveryModule>(DiscoveryModule.TYPE)
		return disco.info(jid, command)
	}

	fun executeCommand(
		jid: JID?, command: String, form: Element? = null, action: Action? = Action.Execute, sessionId: String? = null
	): RequestBuilder<AdHocResponse, IQ> {
		return context.request.iq {
			to = jid
			type = IQType.Set
			"command"{
				xmlns = XMLNS
				attributes["node"] = command
				if (action != null) attributes["action"] = action.xmppValue
				if (sessionId != null) attributes["sessionid"] = sessionId
				if (form != null) {
					addChild(form)
				}
			}
		}.map(::createCommandResult)
	}

	private fun createCommandResult(iq: IQ): AdHocResponse {
		val cmd = iq.getChildrenNS("command", XMLNS) ?: throw XMPPException(
			ErrorCondition.NotAcceptable, "Missing command element."
		)
		val sessionId = cmd.attributes["sessionid"]
		val node = cmd.attributes["node"] ?: throw XMPPException(ErrorCondition.NotAcceptable, "Missing node name.")
		val status = Status.values().first { it.xmppValue == cmd.attributes["status"] }

		val form = cmd.getChildrenNS("x", JabberDataForm.XMLNS)?.let { JabberDataForm(it) }

		val acEl = cmd.getFirstChild("actions")

		val (actions, defaultAction) = if (acEl != null) {
			val d = Action.values().firstOrNull { it.xmppValue == acEl.attributes["execute"] }
			val asz =
				acEl.children.mapNotNull { c -> Action.values().firstOrNull { it.xmppValue == c.name } }.toTypedArray()
			Pair(asz, d)
		} else {
			Pair<Array<Action>, Action?>(emptyArray(), null)
		}

		val notes = cmd.getChildren("note").map(this@CommandsModule::createNote).toTypedArray()

		return AdHocResponse(iq.from, node, sessionId, status, form, actions, defaultAction, notes)
	}

	private fun createNote(e: Element): Note {
		return when (e.attributes["type"]) {
			"warn" -> Note.Warn(e.value)
			"info" -> Note.Info(e.value)
			"error" -> Note.Error(e.value)
			else -> throw XMPPException(ErrorCondition.NotAcceptable, "Unknown note type.")
		}
	}

}