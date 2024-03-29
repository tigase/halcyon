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
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.modules.jingle.Jingle.Session.State
import kotlin.properties.Delegates

abstract class AbstractJingleSession(
	private val sessionManager: AbstractJingleSessionManager<AbstractJingleSession>,
	private val jingleModule: JingleModule,
	override val account: BareJID,
	jid: JID,
	override val sid: String,
	val role: Content.Creator,
	private val initiationType: InitiationType,
) : Jingle.Session {

	override var state: State by Delegates.observable(State.Created) { _, _, newValue ->
		stateChanged(newValue)
	}
		protected set
	override var jid: JID = jid
		protected set

	private var remoteContents: List<Content>? = null
	private var remoteBundles: List<String>? = null

	protected abstract fun stateChanged(state: State)
	protected abstract fun setRemoteDescription(contents: List<Content>, bundle: List<String>?)
	abstract fun addCandidate(candidate: Candidate, contentName: String)

	fun initiate(contents: List<Content>, bundle: List<String>?, completionHandler: AsyncResult<Unit>) {
		jingleModule.initiateSession(jid, sid, contents, bundle)
			.response { r ->
				if (r.isFailure) {
					this.terminate()
				}
				completionHandler(r)
			}
			.send()
	}

	fun initiate(descriptions: List<MessageInitiationDescription>, completionHandler: AsyncResult<Unit>) {
		jingleModule.sendMessageInitiation(MessageInitiationAction.Propose(sid, descriptions), jid)
			.response { r ->
				if (r.isFailure) {
					this.terminate()
				}
				completionHandler(r)
			}
			.send()
	}

	fun initiated(contents: List<Content>, bundle: List<String>?) {
		state = State.Initiating
		remoteContents = contents
		remoteBundles = bundle
	}

	fun accept() {
		state = State.Accepted
		remoteContents?.let { contents ->
			setRemoteDescription(contents, remoteBundles)
		} ?: jingleModule.sendMessageInitiation(MessageInitiationAction.Proceed(sid), jid)
	}

	fun accept(contents: List<Content>, bundle: List<String>?, completionHandler: AsyncResult<Unit>) {
		jingleModule.acceptSession(jid, sid, contents, bundle)
			.response { result ->
				when {
					result.isSuccess -> state = State.Accepted
					result.isFailure -> terminate()
				}
				completionHandler(result)
			}
			.send()
	}

	fun accepted(by: JID) {
		this.state = State.Accepted
		this.jid = by
	}

	fun accepted(contents: List<Content>, bundle: List<String>?) {
		this.state = State.Accepted
		remoteContents = contents
		remoteBundles = bundle
		setRemoteDescription(contents, bundle)
	}

	@Suppress("unused")
	fun decline() {
		terminate(reason = TerminateReason.Decline)
	}

	override fun terminate(reason: TerminateReason) {
		val oldState = state
		if (oldState == State.Terminated) {
			return
		}
		state = State.Terminated
		if (initiationType == InitiationType.Iq || oldState == State.Accepted) {
			jingleModule.terminateSession(jid, sid, reason)
				.send()
		} else {
			jingleModule.sendMessageInitiation(MessageInitiationAction.Reject(sid), jid)
				.send()
		}
		terminateSession()
	}

	fun terminated() {
		if (state == State.Terminated) {
			return
		}
		state = State.Terminated
		terminateSession()
	}

	@Suppress("MemberVisibilityCanBePrivate")
	protected fun terminateSession() {
		sessionManager.close(this)
	}

	@Suppress("unused")
	fun sendCandidate(contentName: String, creator: Content.Creator, transport: Transport) {
		jingleModule.transportInfo(jid, sid, listOf(Content(creator, contentName, null, listOf(transport))))
			.send()
	}
}