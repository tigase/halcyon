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

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import tigase.halcyon.core.Context
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.modules.Criteria
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.ModulesManager
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.response
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.bareJID
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.modules.discovery.NodeDetailsProvider
import tigase.halcyon.core.xmpp.nextUIDLongs
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType
import tigase.halcyon.core.xmpp.stanzas.wrap

/**
 * Status of command execution.
 */
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

/**
 * Command action.
 */
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

/**
 * Note.
 */
sealed class Note(val message: String?) {

    /**
     * The note is informational only. This is not really an exceptional condition.
     */
    class Info(message: String?) : Note(message)

    /**
     * The note indicates a warning. Possibly due to illogical (yet valid) data.
     */
    class Warn(message: String?) : Note(message)

    /**
     * The note indicates an error. The text should indicate the reason for the error.
     */
    class Error(message: String?) : Note(message)
}

data class AdHocResult(
    /**
     * JabberID of command executor.
     */
    val jid: JID?,
    /**
     * Name of command.
     */
    val node: String,
    /**
     * Session identifier.
     */
    val sessionId: String?,
    /**
     * Command execution status.
     */
    val status: Status,
    /**
     * Data form.
     */
    val form: JabberDataForm?,
    /**
     * Available actions.
     */
    val actions: Array<Action>,
    /**
     * Default action for next command execution .
     */
    val defaultAction: Action?,
    /**
     * Command notes.
     */
    val notes: Array<Note>
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdHocResult) return false

        if (jid != other.jid) return false
        if (node != other.node) return false
        if (sessionId != other.sessionId) return false
        if (status != other.status) return false
        if (form != other.form) return false
        if (!actions.contentEquals(other.actions)) return false
        if (defaultAction != other.defaultAction) return false
        return notes.contentEquals(other.notes)
    }

    override fun hashCode(): Int {
        var result = jid?.hashCode() ?: 0
        result = 31 * result + node.hashCode()
        result = 31 * result + (sessionId?.hashCode() ?: 0)
        result = 31 * result + status.hashCode()
        result = 31 * result + (form?.hashCode() ?: 0)
        result = 31 * result + actions.contentHashCode()
        result = 31 * result + (defaultAction?.hashCode() ?: 0)
        result = 31 * result + notes.contentHashCode()
        return result
    }
}

/**
 * Interface for the implementation of custom AdHoc Commands.
 */
interface AdHocCommand {

    /**
     * Returns `true` if given JabberID is allowed to execute this command.
     */
    fun isAllowed(jid: BareJID): Boolean

    /**
     * Command node name (must be unique in client).
     */
    val node: String

    /**
     * Human-readable name of command.
     */
    val name: String

    fun process(request: AdHocRequest, response: AdHocResponse)
}

interface AdHocSession {

    val sessionId: String
    val values: MutableMap<String, Any>
}

interface AdHocRequest {

    val stanza: IQ

    val command: AdHocCommand

    val action: Action?

    val form: JabberDataForm?

    /**
     * Returns current session, or creates new one.
     */
    fun getSession(): AdHocSession

    /**
     * Returns current session or `null` if session is not assigned to request.
     */
    fun getSessionOrNull(): AdHocSession?
}

interface AdHocResponse {

    var status: Status
    var form: JabberDataForm?
    var actions: Array<Action>
    var defaultAction: Action?
    var notes: Array<Note>
}

interface CommandsModuleConfig

/**
 * Module is implementing Ad-Hoc Commands ([XEP-0050](https://xmpp.org/extensions/xep-0050.html)).
 */
class CommandsModule(override val context: Context, private val discoveryModule: DiscoveryModule) :
    XmppModule,
    CommandsModuleConfig {

    /**
     * Module is implementing Ad-Hoc Commands ([XEP-0050](https://xmpp.org/extensions/xep-0050.html)).
     */
    companion object : XmppModuleProvider<CommandsModule, CommandsModuleConfig> {

        const val XMLNS = "http://jabber.org/protocol/commands"
        override val TYPE = XMLNS
        const val NODE = XMLNS

        override fun instance(context: Context): CommandsModule =
            CommandsModule(context, context.modules.getModule(DiscoveryModule))

        override fun configure(module: CommandsModule, cfg: CommandsModuleConfig.() -> Unit) =
            module.cfg()

        override fun requiredModules() = listOf(DiscoveryModule)

        override fun doAfterRegistration(module: CommandsModule, moduleManager: ModulesManager) =
            module.initialize()
    }

    override val type = TYPE
    override val criteria: Criteria = Criterion.chain(
        Criterion.name(IQ.NAME),
        Criterion.nameAndXmlns("command", XMLNS)
    )
    override val features: Array<String> = arrayOf(XMLNS)

    inner class AdHocCommandsNodeDetailsProvider : NodeDetailsProvider {

        override fun getIdentities(
            sender: BareJID?,
            node: String?
        ): List<DiscoveryModule.Identity> {
            if (sender == null || node == null) return emptyList()
            val cmd = registeredCommands[node] ?: return emptyList()
            return getCommandIdentities(sender, cmd)
        }

        override fun getFeatures(sender: BareJID?, node: String?): List<String> {
            if (node == null) return emptyList()
            if (registeredCommands.containsKey(
                    node
                )
            ) {
                return listOf(XMLNS, JabberDataForm.XMLNS)
            } else {
                return emptyList()
            }
        }

        override fun getItems(sender: BareJID?, node: String?): List<DiscoveryModule.Item> {
            if (sender != null && node == NODE) {
                return getCommandItems(sender)
            } else {
                return emptyList()
            }
        }
    }

    private class AdHocSessionImpl(
        override val sessionId: String,
        override val values: MutableMap<String, Any> = mutableMapOf()
    ) : AdHocSession {

        // 		val creationDate: Instant = Clock.System.now()
        var lastAccessDate: Instant = Clock.System.now()
    }

    private class AdHocRequestImpl(
        override val stanza: IQ,
        override val command: AdHocCommand,
        override val form: JabberDataForm?,
        override val action: Action?
    ) : AdHocRequest {

        var adHocSession: AdHocSession? = null

        override fun getSession(): AdHocSession {
            if (adHocSession == null) adHocSession = AdHocSessionImpl(nextUIDLongs())
            return adHocSession
                ?: throw HalcyonException("Internal error. AdHoc Session is not created.")
        }

        override fun getSessionOrNull(): AdHocSession? = adHocSession
    }

    private class AdHocResponseImpl : AdHocResponse {

        override var status: Status = Status.Completed
        override var form: JabberDataForm? = null
        override var actions: Array<Action> = emptyArray()
        override var defaultAction: Action? = null
        override var notes: Array<Note> = emptyArray()
    }

    private val registeredCommands = mutableMapOf<String, AdHocCommand>()
    private val sessions: MutableMap<String, AdHocSessionImpl> = mutableMapOf()

    private fun initialize() {
        discoveryModule.addNodeDetailsProvider(AdHocCommandsNodeDetailsProvider())
    }

    /**
     * Registers custom Ad-Hoc command.
     */
    fun registerAdHocCommand(command: AdHocCommand) {
        this.registeredCommands[command.node] = command
    }

    override fun process(element: Element) {
        val stanza = wrap<IQ>(element)
        val sender =
            stanza.from?.bareJID
                ?: throw XMPPException(ErrorCondition.Forbidden, "Sender is not specified.")
        val cmdElement =
            stanza.getChildrenNS("command", XMLNS)
                ?: throw HalcyonException("Command element is missing")
        val nodeName =
            cmdElement.attributes["node"]
                ?: throw XMPPException(ErrorCondition.BadRequest, "Missing node attribute.")

        val adHoc =
            registeredCommands[nodeName]
                ?: throw XMPPException(ErrorCondition.ItemNotFound, "Unknown command.")
        if (!adHoc.isAllowed(sender)) throw XMPPException(ErrorCondition.Forbidden)

        val action = Action.values().firstOrNull { it.xmppValue == cmdElement.attributes["action"] }

        val form = cmdElement.getChildrenNS("x", JabberDataForm.XMLNS)?.let { JabberDataForm(it) }

        val adHocRequest = AdHocRequestImpl(stanza, adHoc, form, action)
        cmdElement.attributes["sessionid"]?.let { sessionId -> sessions[sessionId] }?.let {
            adHocRequest.adHocSession = it
            it.lastAccessDate = Clock.System.now()
        }
        val adHocResponse = AdHocResponseImpl()

        adHoc.process(adHocRequest, adHocResponse)

        if (action == Action.Cancel) {
            adHocResponse.status = Status.Canceled
        }

        context.writer.writeDirectly(
            response(stanza) {
                "command" {
                    xmlns = XMLNS
                    attributes["node"] = nodeName
                    adHocRequest.getSessionOrNull()?.let {
                        attributes["sessionid"] = it.sessionId
                    }
                    attributes["status"] = adHocResponse.status.xmppValue
                    if (adHocResponse.actions.isNotEmpty()) {
                        "actions" {
                            adHocResponse.defaultAction?.let {
                                attributes["execute"] = it.xmppValue
                            }
                            adHocResponse.actions.forEach {
                                it.xmppValue {}
                            }
                        }
                    }
                    adHocResponse.form?.let {
                        addChild(it.element)
                    }
                    adHocResponse.notes.forEach { note ->
                        "note" {
                            attributes["type"] = when (note) {
                                is Note.Info -> "info"
                                is Note.Error -> "error"
                                is Note.Warn -> "warn"
                            }
                            +"${note.message}"
                        }
                    }
                }
            }
        )

        adHocRequest.getSessionOrNull()?.let {
            if (adHocResponse.status == Status.Executing) {
                sessions[it.sessionId] = it as AdHocSessionImpl
            } else {
                sessions.remove(it.sessionId)
            }
        }
    }

    /**
     * Prepares request to retrieve list of commands provided by entity.
     * @param jid JabberID of entity.
     */
    fun retrieveCommandList(jid: JID?): RequestBuilder<DiscoveryModule.Items, IQ> =
        discoveryModule.items(jid, NODE)

    /**
     * Prepares request to retrieve [Info][DiscoveryModule.Info]) data about command.
     * @param jid JabberID of entity.
     * @param command Ad-Hoc command name.
     */
    fun retrieveCommandInfo(jid: JID?, command: String): RequestBuilder<DiscoveryModule.Info, IQ> =
        discoveryModule.info(jid, command)

    /**
     * Prepare request to execute AdHoc command.
     * @param jid JabbeID of entity.
     * @param command Ad-Hoc Command name.
     * @param form Form with data to send.
     * @param action Command action.
     * @param sessionId Identifier of AdHoc command session.
     */
    fun executeCommand(
        jid: JID?,
        command: String,
        form: Element? = null,
        action: Action? = Action.Execute,
        sessionId: String? = null
    ): RequestBuilder<AdHocResult, IQ> = context.request.iq {
        to = jid
        type = IQType.Set
        "command" {
            xmlns = XMLNS
            attributes["node"] = command
            if (action != null) attributes["action"] = action.xmppValue
            if (sessionId != null) attributes["sessionid"] = sessionId
            if (form != null) {
                addChild(form)
            }
        }
    }.map(::createCommandResult)

    private fun createCommandResult(iq: IQ): AdHocResult {
        val cmd = iq.getChildrenNS("command", XMLNS) ?: throw XMPPException(
            ErrorCondition.NotAcceptable,
            "Missing command element."
        )
        val sessionId = cmd.attributes["sessionid"]
        val node =
            cmd.attributes["node"]
                ?: throw XMPPException(ErrorCondition.NotAcceptable, "Missing node name.")
        val status = Status.values().first { it.xmppValue == cmd.attributes["status"] }

        val form = cmd.getChildrenNS("x", JabberDataForm.XMLNS)?.let { JabberDataForm(it) }

        val acEl = cmd.getFirstChild("actions")

        val (actions, defaultAction) = if (acEl != null) {
            val d = Action.values().firstOrNull { it.xmppValue == acEl.attributes["execute"] }
            val asz = acEl.children.mapNotNull { c ->
                Action.values().firstOrNull { it.xmppValue == c.name }
            }.toTypedArray()
            Pair(asz, d)
        } else {
            Pair<Array<Action>, Action?>(emptyArray(), null)
        }

        val notes = cmd.getChildren("note").map(this@CommandsModule::createNote).toTypedArray()

        return AdHocResult(iq.from, node, sessionId, status, form, actions, defaultAction, notes)
    }

    private fun createNote(e: Element): Note = when (e.attributes["type"]) {
        "warn" -> Note.Warn(e.value)
        "info" -> Note.Info(e.value)
        "error" -> Note.Error(e.value)
        else -> throw XMPPException(ErrorCondition.NotAcceptable, "Unknown note type.")
    }

    private fun getCommandIdentities(
        sender: BareJID,
        cmd: AdHocCommand
    ): List<DiscoveryModule.Identity> {
        if (!cmd.isAllowed(sender)) throw XMPPException(ErrorCondition.Forbidden)
        return listOf(DiscoveryModule.Identity("automation", "command-node", cmd.name))
    }

    private fun getCommandItems(sender: BareJID): List<DiscoveryModule.Item> {
        val jid = context.boundJID ?: throw XMPPException(ErrorCondition.ServiceUnavailable)
        return registeredCommands.filter { it.value.isAllowed(sender) }
            .map { DiscoveryModule.Item(jid, it.value.name, it.key) }
    }
}
