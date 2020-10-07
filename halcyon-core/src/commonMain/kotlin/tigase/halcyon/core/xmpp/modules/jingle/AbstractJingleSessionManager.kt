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
package tigase.halcyon.core.xmpp.modules.jingle

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.modules.presence.ContactChangeStatusEvent
import tigase.halcyon.core.xmpp.stanzas.PresenceType

abstract class AbstractJingleSessionManager<S : AbstractJingleSession>(
	name: String, private val sessionFactory: SessionFactory<S>
) : Jingle.SessionManager {

	private val log: Logger = Logger(name)

	protected var sessions: List<S> = emptyList()

	private val jingleEventHandler: (JingleEvent) -> Unit = { event ->
		when (event.action) {
			Action.sessionInitiate -> sessionInitiated(event)
			Action.sessionAccept -> sessionAccepted(event)
			Action.transportInfo -> transportInfo(event)
			Action.sessionTerminate -> sessionTerminated(event)
			else -> log.warning("unsupported event: " + event.action.name)
		}
	}
	private val contactChangeStatusEventHandler: (ContactChangeStatusEvent) -> Unit = { event ->
		if (event.lastReceivedPresence.type == PresenceType.Unavailable) {
			val toClose =
				sessions.filter { it.jid == event.presence.from && it.account == event.context.config.userJID }
			toClose.forEach { it.terminate(TerminateReason.success) }
		}
	}
	private val jingleMessageInitiationEvent: (JingleMessageInitiationEvent) -> Unit = { event ->
		val account = event.context.config.userJID!!
		when (event.action) {
			is MessageInitiationAction.Propose -> {
				if (session(account, event.jid, event.action.id) == null) {
					val session = open(
						event.context.getModule<JingleModule>(JingleModule.TYPE)!!,
						account,
						event.jid,
						event.action.id,
						Content.Creator.responder,
						InitiationType.message
					)
					val media = event.action.descriptions.filter { isDesciptionSupported(it) }.map { it.media }
					fireIncomingSessionEvent(event.context, session, media)
				}
			}
			is MessageInitiationAction.Retract -> sessionTerminated(account, event.jid, event.action.id)
			is MessageInitiationAction.Accept -> sessionTerminated(account, event.action.id)
			is MessageInitiationAction.Reject -> sessionTerminated(account, event.jid, event.action.id)
			is MessageInitiationAction.Proceed -> session(account, event.jid, event.action.id)?.let { session ->
				session.accepted(event.jid)
			}
		}
	}

	abstract fun isDesciptionSupported(descrition: MessageInitiationDescription): Boolean

	fun register(halcyon: AbstractHalcyon) {
		halcyon.eventBus.register(JingleEvent.TYPE, this.jingleEventHandler)
		halcyon.eventBus.register(ContactChangeStatusEvent.TYPE, this.contactChangeStatusEventHandler)
		halcyon.eventBus.register(JingleMessageInitiationEvent.TYPE, this.jingleMessageInitiationEvent)
	}

	fun unregister(halcyon: AbstractHalcyon) {
		// TODO: how to remove those handlers??
	}

	override fun activateSessionSid(account: BareJID, jid: JID): String? {
		return session(account, jid, null)?.sid
	}

	fun session(account: BareJID, jid: JID, sid: String?): S? =
		sessions.firstOrNull { it.account == account && (sid == null || it.sid == sid) && (it.jid == jid || (it.jid.resource == null && it.jid.bareJID == jid.bareJID)) }

	fun open(
		jingleModule: JingleModule,
		account: BareJID,
		jid: JID,
		sid: String,
		role: Content.Creator,
		initiationType: InitiationType
	): S {
		val session = sessionFactory.createSession(this, jingleModule, account, jid, sid, role, initiationType)
		sessions += session
		return session
	}

	fun close(account: BareJID, jid: JID, sid: String): S? = session(account, jid, sid)?.let { session ->
		sessions -= session
		return session
	}

	fun close(session: S) {
		close(session.account, session.jid, session.sid)
	}

	protected fun sessionInitiated(event: JingleEvent) {
		val account = event.context.config.userJID!!
		session(account, event.jid, event.sid)?.let { session ->
			session.accepted(event.contents, event.bundle)
		} ?: {
			val session = open(
				event.context.getModule<JingleModule>(JingleModule.TYPE)!!,
				account,
				event.jid,
				event.sid,
				Content.Creator.responder,
				InitiationType.iq
			)
			session.initiated(event.contents, event.bundle)
			fireIncomingSessionEvent(
				event.context, session, event.contents.map { it.description?.media }.filterNotNull()
			)
		}()
	}

	protected fun sessionAccepted(event: JingleEvent) {
		val account = event.context.config.userJID!!
		session(account, event.jid, event.sid)?.let { it.accepted(event.contents, event.bundle) }
	}

	protected fun sessionTerminated(event: JingleEvent) {
		val account = event.context.config.userJID!!
		sessionTerminated(account, event.jid, event.sid)
	}

	protected fun sessionTerminated(account: BareJID, sid: String) {
		val toTerminate = sessions.filter { it.account == account && it.sid == sid }
		toTerminate.forEach { it.terminated() }
	}

	protected fun sessionTerminated(account: BareJID, jid: JID, sid: String) {
		session(account, jid, sid)?.terminated()
	}

	protected fun transportInfo(event: JingleEvent) {
		val account = event.context.config.userJID!!
		session(account, event.jid, event.sid)?.let { session ->
			for (content in event.contents) {
				content.transports.flatMap { it.candidates }.forEach { session.addCandidate(it, content.name) }
			}
		}
	}

	interface SessionFactory<S : AbstractJingleSession> {

		fun createSession(
			jingleSessionManager: AbstractJingleSessionManager<S>,
			jingleModule: JingleModule,
			account: BareJID,
			jid: JID,
			sid: String,
			role: Content.Creator,
			initiationType: InitiationType
		): S
	}

	protected fun fireIncomingSessionEvent(context: AbstractHalcyon, session: S, media: List<String>) {
		context.eventBus.fire(JingleIncomingSessionEvent(session, media))
	}
}

class JingleIncomingSessionEvent(val session: AbstractJingleSession, val media: List<String>) : Event(TYPE) {

	companion object {

		const val TYPE = "tigase.halcyon.core.xmpp.modules.jingle.JingleIncomingSession"
	}
}