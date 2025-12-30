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
package tigase.halcyon.core.xmpp.modules.jingle

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.Context
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.eventbus.handler
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.utils.Lock
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.modules.presence.ContactChangeStatusEvent
import tigase.halcyon.core.xmpp.stanzas.PresenceType

abstract class AbstractJingleSessionManager<S : AbstractJingleSession>(
	name: String
) : Jingle.SessionManager {

	abstract fun createSession(context: Context, account: BareJID, jid: JID, sid: String, role: Content.Creator, initiationType: InitiationType): S
	abstract fun reportIncomingCallAction(session: S, action: MessageInitiationAction)

	private val log = LoggerFactory.logger(name)

	protected var sessions: List<S> = emptyList()
	private val lock = Lock();
	
	private val contactChangeStatusEventHandler = handler<ContactChangeStatusEvent> { event ->
		if (event.lastReceivedPresence.type == PresenceType.Unavailable) {
			val toClose =
				sessions.filter { it.jid == event.presence.from && it.account == event.context.boundJID?.bareJID }
			toClose.forEach { it.terminate(TerminateReason.Gone) }
		}
	}

	abstract fun isDescriptionSupported(descrition: MessageInitiationDescription): Boolean

	fun register(halcyon: AbstractHalcyon) {
		halcyon.eventBus.register(ContactChangeStatusEvent, this.contactChangeStatusEventHandler)
	}

	fun unregister(halcyon: AbstractHalcyon) {
		halcyon.eventBus.unregister(ContactChangeStatusEvent, this.contactChangeStatusEventHandler)
	}

	fun session(context: Context, jid: JID, sid: String?): S? {
		return context.boundJID?.bareJID?.let { account ->
			session(account, jid, sid);
		}
	}

	fun session(account: BareJID, jid: JID, sid: String?): S? =
		lock.withLock {
			sessions.firstOrNull { it.account == account && (sid == null || it.sid == sid) && (it.jid == jid || (it.jid.resource == null && it.jid.bareJID == jid.bareJID)) }
		}

	fun open(
		context: Context,
		jid: JID,
		sid: String,
		role: Content.Creator,
		initiationType: InitiationType,
	): S {
		return open(context, context.boundJID?.bareJID!!, jid, sid, role, initiationType)
	}

	fun open(
		context: Context,
		account: BareJID,
		jid: JID,
		sid: String,
		role: Content.Creator,
		initiationType: InitiationType,
	): S {
		return lock.withLock {
			val session = this.createSession(context, account, jid, sid, role, initiationType);
			sessions = sessions + session
			return@withLock session
		}
	}

	fun close(account: BareJID, jid: JID, sid: String): S? = lock.withLock {
		return@withLock session(account, jid, sid)?.let { session ->
			sessions = sessions - session
			return@let session
		}
	}

	fun close(session: AbstractJingleSession) {
		close(session.account, session.jid, session.sid)
	}

	enum class ContentType {
		audio,
		video,
		filetransfer
	}

	enum class Media {
		audio,
		video
	}

	override fun messageInitiation(context: Context, fromJid: JID, action: MessageInitiationAction) {
		when (action) {
			is MessageInitiationAction.Propose -> {
				if (this.session(context, fromJid, action.id) != null) {
					return;
				}
				val session = open(context, context.boundJID?.bareJID!!, fromJid, action.id, Content.Creator.responder, InitiationType.Message);
				reportIncomingCallAction(session, action);
			}
			is MessageInitiationAction.Ringing -> {
				session(context, fromJid, action.id)?.let {
					reportIncomingCallAction(it, action);
				}
			}
			is MessageInitiationAction.Retract -> {
				session(context, fromJid, action.id)?.let {
					reportIncomingCallAction(it, action)
				}
				sessionTerminated(context, fromJid, action.id, TerminateReason.Cancel)
			}
			is MessageInitiationAction.Accept -> {
				session(context, fromJid, action.id)?.let {
					reportIncomingCallAction(it, action)
				}
				sessionTerminated(context.boundJID!!.bareJID, action.id, null);
			}
			is MessageInitiationAction.Reject -> {
				session(context, fromJid, action.id)?.let {
					reportIncomingCallAction(it, action)
				}
				sessionTerminated(context.boundJID!!.bareJID, action.id, TerminateReason.Decline)
			}
			is MessageInitiationAction.Proceed -> {
				val session = session(context, fromJid, action.id) ?: return;
				reportIncomingCallAction(session, action);
				session.accepted(fromJid);
			}
			is MessageInitiationAction.Finish -> {
				session(context, fromJid, action.id)?.let {
					reportIncomingCallAction(it, action)
				}
				sessionTerminated(context.boundJID!!.bareJID, action.id, action.reason)
			}
		}
	}

	override fun sessionInitiated(context: Context, jid: JID, sid: String, contents: List<Content>, bundle: List<String>?) {
		val sdp = SDP(contents, bundle ?: emptyList());
		val media = sdp.contents.map { it.description?.media?.let {  Media.valueOf(it)} }.filterNotNull()

		log.finest("calling session initiated for jid: ${jid}, sid: $sid, sdp: $media, bundle: $bundle")

		session(context, jid, sid)?.let { session ->
			log.finest("initiating session that already existed for jid: ${jid}, sid: $sid, sdp: $media, bundle: $bundle")
			session.initiated(jid, contents, bundle)
		} ?: run {
			log.finest("creating an initiating session for jid: ${jid}, sid: $sid, sdp: $media, bundle: $bundle")
			val session = open(context, context.boundJID?.bareJID!!, jid, sid, Content.Creator.responder, InitiationType.Iq);
			session.initiated(jid, contents, bundle)
			reportIncomingCallAction(
				session,
				MessageInitiationAction.Propose(
					sid,
					media.map { MessageInitiationDescription("urn:xmpp:jingle:apps:rtp:1", it.name) },
					null
				)
			);
		}
	}

	@Throws(XMPPException::class)
	override fun sessionAccepted(
		context: Context,
		jid: JID,
		sid: String,
		contents: List<Content>,
		bundle: List<String>?
	) {
		val session = session(context, jid, sid) ?: throw XMPPException(ErrorCondition.ItemNotFound);
		session.accepted(contents, bundle);
	}

	override fun sessionTerminated(context: Context, jid: JID, sid: String, reason: TerminateReason?) {
		session(context, jid, sid)?.terminated(reason)
	}

	fun sessionTerminated(account: BareJID, sid: String, reason: TerminateReason?) {
		val toTerminate = lock.withLock {
			return@withLock sessions.filter { it.account == account && it.sid == sid }
		}
		toTerminate.forEach { it.terminated(reason) }
	}

	@Throws(XMPPException::class)
	override fun transportInfo(context: Context, jid: JID, sid: String, contents: List<Content>) {
		val session = session(context, jid, sid) ?: throw XMPPException(ErrorCondition.ItemNotFound);
		for (content in contents) {
			content.transports.flatMap { it.candidates }.forEach { session.addCandidate(it, content.name) }
		}
	}

	protected fun fireIncomingSessionEvent(context: AbstractHalcyon, session: S, media: List<String>) {
		context.eventBus.fire(JingleIncomingSessionEvent(session, media))
	}
}

class JingleIncomingSessionEvent(val session: AbstractJingleSession, @Suppress("unused") val media: List<String>) :
	Event(TYPE) {

	companion object : EventDefinition<JingleIncomingSessionEvent> {

		override val TYPE = "tigase.halcyon.core.xmpp.modules.jingle.JingleIncomingSession"
	}
}