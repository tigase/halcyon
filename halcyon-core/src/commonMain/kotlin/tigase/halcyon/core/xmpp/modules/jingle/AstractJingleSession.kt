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

import tigase.halcyon.core.AsyncResult
import tigase.halcyon.core.Context
import tigase.halcyon.core.ReflectionModuleManager
import tigase.halcyon.core.utils.Lock
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.bareJID
import tigase.halcyon.core.xmpp.modules.jingle.Jingle.Session.State

@OptIn(ReflectionModuleManager::class)
abstract class AbstractJingleSession(
	private val terminateFunction: (AbstractJingleSession)->Unit,
	context: Context,
	jid: JID,
	override val sid: String,
	val role: Content.Creator,
	val initiationType: InitiationType,
) : Jingle.Session {

	final override val account: BareJID
	private val jingleModule: JingleModule

	interface StateDelegate {
		fun stateChanged(state: State);
	}

	var stateDelegate: StateDelegate? = null
		set(value) {
			lock.withLock {
				field = value
			}
			value?.stateChanged(state)
		}

	override var state: State = State.Created
		get() {
			return lock.withLock {
				field;
			}
		}
		protected set(value) {
			lock.withLock {
				if (field != value) {
					field = value
					stateDelegate
				} else {
					null
				}
			}?.stateChanged(value);
		}
	override var jid: JID = jid
		protected set

	private var remoteContents: List<Content>? = null
	private var remoteBundles: List<String>? = null

	init {
		this.account = context.boundJID!!.bareJID;
		this.jingleModule = context.modules.getModule<JingleModule>()
	}


	interface ActionDelegate {
		fun received(action: Action)
	}

	var actionDelegate: ActionDelegate? = null
		set(value) {
			lock.withLock {
				field = value;
				value?.let { delegate ->
					while (!actionsQueue.isEmpty()) {
						val action = actionsQueue.removeFirst()
						delegate.received(action);
					}
				}
			}
		}

	private var actionsQueue: ArrayDeque<Action> = ArrayDeque<Action>()

	sealed class Action: Comparable<Action> {
		class ContentSet(val sdp: SDP): Action() {}
		class ContentApply(val contentAction: Jingle.ContentAction, val sdp: SDP): Action() {}
		class TransportAdd(val candidate: Candidate, val contentName: String): Action() {}
		class SessionInfo(val infos: List<Jingle.SessionInfo>): Action() {}

		var order: Int = when(this) {
			is ContentSet -> 0
			is ContentApply -> 0
			is TransportAdd -> 1
			is SessionInfo -> 2                   
		}

		override fun compareTo(other: Action): Int {
			val x = this.order;
			val y = other.order;
			return if ((x < y)) -1 else (if ((x == y)) 0 else 1)
		}
	}

	private fun received(action: Action) {
		lock.withLock {
			if (actionDelegate != null) {
				actionDelegate
			} else {
				val idx = actionsQueue.indexOfFirst { it.order > action.order };
				if (idx < 0) {
					actionsQueue.add(action)
				} else {
					actionsQueue.add(idx, action)
				}
				null
			}
		}?.received(action)
	}

	fun initiate(contents: List<Content>, bundle: List<String>?, completionHandler: AsyncResult<Unit>) {
		jingleModule.initiateSession(jid, sid, contents, bundle)
			.response { r ->
				if (r.isFailure) {
					this.terminate(TerminateReason.GeneralError)
				}
				completionHandler(r)
			}
			.send()
	}

	fun initiate(descriptions: List<MessageInitiationDescription>, data: Set<Element>? = null, completionHandler: AsyncResult<Unit>) {
		jingleModule.sendMessageInitiation(MessageInitiationAction.Propose(sid, descriptions, data?.toList()), jid.bareJID)
			.response { r ->
				if (r.isFailure) {
					this.terminate(TerminateReason.GeneralError)
				}
				completionHandler(r)
			}
			.send()
	}

	fun startedRinging() {
		jingleModule.sendMessageInitiation(MessageInitiationAction.Ringing(sid), jid.bareJID).response { r ->
			// nothing to do, but catching all errors...
		}.send()
	}

	private val lock = Lock();
	private var contentCreators = HashMap<String, Content.Creator>();
	fun contentCreator(contentName: String): Content.Creator {
		return lock.withLock {
			return@withLock contentCreators.get(contentName) ?: this.role;
		}
	}

	private fun updateCreators(contents: List<Content>) {
		lock.withLock {
			for (content in contents) {
				if (!contentCreators.containsKey(content.name)) {
					contentCreators.put(content.name, content.creator);
				}
			}
		}
	}

	fun initiated(jid: JID, contents: List<Content>, bundle: List<String>?) {
		lock.withLock {
			this.jid = jid;
			updateCreators(contents);
			state = State.Initiating
			remoteContents = contents
			remoteBundles = bundle
		}
		received(Action.ContentSet(SDP(contents, bundle ?: emptyList())))
	}

	fun accept() {
		lock.withLock {
			state = State.Accepted
			if (initiationType == InitiationType.Message) {
				jingleModule.sendMessageInitiation(MessageInitiationAction.Proceed(sid), jid.bareJID).send()
			}
		}
	}

	fun accept(contents: List<Content>, bundle: List<String>?, completionHandler: AsyncResult<Unit>) {
		updateCreators(contents);
		jingleModule.acceptSession(jid, sid, contents, bundle)
			.response { result ->
				when {
					result.isSuccess -> state = State.Accepted
					result.isFailure -> terminate(TerminateReason.GeneralError)
				}
				completionHandler(result)
			}
			.send()
	}

	fun accepted(by: JID) {
		lock.withLock {
			this.jid = by
			this.state = State.Accepted
		}
	}

	fun accepted(contents: List<Content>, bundle: List<String>?) {
		lock.withLock {
			this.state = State.Accepted
			remoteContents = contents
			remoteBundles = bundle
		}
		received(Action.ContentSet(SDP(contents, bundle ?: emptyList())))
	}

	fun sessionInfo(actions: List<Jingle.SessionInfo>) {
		jingleModule.sessionInfo(jid, sid, actions, creatorProvider = this::contentCreator).send();
	}

	fun transportInfo(contentName: String, transport: Transport) {
		val creator = contentCreator(contentName);
		jingleModule.transportInfo(jid, sid, listOf(Content(creator, null, contentName, null, listOf(transport)))).send()
	}

	fun contentModify(action: Jingle.ContentAction, contents: List<Content>, bundle: List<String>?) {
		jingleModule.contentModify(jid, sid, action, contents, bundle).send()
	}

	fun contentModified(action: Jingle.ContentAction, contents: List<Content>, bundle: List<String>?) {
		val sdp = SDP(contents, bundle ?: emptyList());
		received(Action.ContentApply(action, sdp));
	}

	fun sessionInfoReceived(info: List<Jingle.SessionInfo>) {
		received(Action.SessionInfo(info));
	}

	fun addCandidate(candidate: Candidate, contentName: String) {
		received(Action.TransportAdd(candidate, contentName));
	}

	override fun terminate(reason: TerminateReason?) {
		var oldState: Jingle.Session.State = State.Terminated(reason);
		if (!lock.withLock {
			oldState = state
			if (oldState is State.Terminated) {
				return@withLock false;
			}
			state = State.Terminated(reason)
			return@withLock true;
		}) {
			return;
		}

		when (initiationType) {
			InitiationType.Iq -> jingleModule.terminateSession(jid, sid, reason)
				.send();
			InitiationType.Message -> {
				if (oldState != State.Created) {
					jingleModule.terminateSession(jid, sid, reason)
						.send()
				}
				if (oldState == State.Created) {
					when (role) {
						Content.Creator.initiator -> {
							jingleModule.sendMessageInitiation(MessageInitiationAction.Retract(sid, reason), jid.bareJID)
								.send()
						}
						Content.Creator.responder -> {
							jingleModule.sendMessageInitiation(MessageInitiationAction.Reject(sid, reason), jid.bareJID)
								.send()
						}
					}
				} else {
					jingleModule.sendMessageInitiation(MessageInitiationAction.Finish(sid, reason), jid.bareJID)
						.send()
				}
			}
		}
		terminateSession()
	}

	fun terminated(reason: TerminateReason?) {
		if (!lock.withLock {
				if (state is State.Terminated) {
					return@withLock false
				}
				state = State.Terminated(reason)
				return@withLock true;
			}
		) {
			return;
		}
		terminateSession()
	}

	@Suppress("MemberVisibilityCanBePrivate")
	protected fun terminateSession() {
		terminateFunction(this);
		//sessionManager.close(this)
	}
	
}