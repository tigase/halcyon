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

import tigase.halcyon.core.AsyncResult
import tigase.halcyon.core.Result
import tigase.halcyon.core.mapToResult
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import kotlin.properties.Delegates
import tigase.halcyon.core.xmpp.modules.jingle.Jingle.Session.State;

abstract class AbstractJingleSession(
    private val sessionManager: AbstractJingleSessionManager<AbstractJingleSession>,
    private val jingleModule: JingleModule,
    override val account: BareJID,
    jid: JID,
    override val sid: String,
    val role: Content.Creator,
    val initiationType: InitiationType
): Jingle.Session {
    override var state: Jingle.Session.State by Delegates.observable(Jingle.Session.State.created) { property, oldValue, newValue -> stateChanged(newValue)  }
        protected set;
    override lateinit var jid: JID
        protected set;

    private var remoteContents: List<Content>? = null;
    private var remoteBundles: List<String>? = null;

    init {
        this.jid = jid;
    }

    protected abstract fun stateChanged(state: Jingle.Session.State);
    protected abstract fun setRemoteDescription(contents: List<Content>, bundle: List<String>?);
    abstract fun addCandidate(candidate: Candidate, contentName: String);

    fun initiate(contents: List<Content>, bundle: List<String>?, completionHandler: AsyncResult<Unit, ErrorCondition>){
        jingleModule.initiateSession(jid, sid, contents, bundle).response { result ->
            val r = result.mapToResult { v -> v ?: Unit  };
            when (r) {
                is Result.Failure -> this.terminate();
            }
            completionHandler(r);
        }.send();
    }

    fun initiate(descriptions: List<MessageInitiationDescription>, completionHandler: AsyncResult<Unit, ErrorCondition>) {
        jingleModule.sendMessageInitiation(MessageInitiationAction.Propose(sid, descriptions), jid).result { result ->
            val r = result.mapToResult();
            when (r) {
                is Result.Failure -> this.terminate();
            }
            completionHandler(r);
        }.send();
    }

    fun initiated(contents: List<Content>, bundle: List<String>?) {
        state = State.initiating;
        remoteContents = contents;
        remoteBundles = bundle;
    }

    fun accept() {
        state = State.accepted;
        remoteContents?.let { contents ->
            setRemoteDescription(contents, remoteBundles);
        } ?: {
            jingleModule.sendMessageInitiation(MessageInitiationAction.Proceed(sid), jid);
        }();
    }

    fun accept(contents: List<Content>, bundle: List<String>?, completionHandler: AsyncResult<Unit, ErrorCondition>) {
        jingleModule.acceptSession(jid, sid, contents, bundle).response { iqResult ->
            val result = iqResult.mapToResult { v -> v ?: Unit }
            when (result) {
                is Result.Success -> state = State.accepted;
                is Result.Failure -> terminate()
            }
            completionHandler(result);
        }.send();
    }

    fun accepted(by: JID) {
        this.state = State.accepted;
        this.jid = by;
    }

    fun accepted(contents: List<Content>, bundle: List<String>?) {
        this.state = State.accepted;
        remoteContents = contents;
        remoteBundles = bundle;
        setRemoteDescription(contents, bundle);
    }

    fun decline() {
        terminate(reason = TerminateReason.decline);
    }

    override fun terminate(reason: TerminateReason) {
        val oldState = state;
        if (oldState == State.terminated) {
            return;
        }
        state = State.terminated;
        if (initiationType == InitiationType.iq || oldState == State.accepted) {
            jingleModule.terminateSession(jid, sid, reason).send();
        } else {
            jingleModule.sendMessageInitiation(MessageInitiationAction.Reject(sid), jid).send();
        }
        terminateSession();
    }

    fun terminated() {
        if (state == State.terminated) {
            return;
        }
        state = State.terminated;
        terminateSession();
    }

    protected fun terminateSession() {
        sessionManager.close(this);
    }

    fun sendCandidate(contentName: String, creator: Content.Creator, transport: Transport) {
        jingleModule.transportInfo(jid, sid, listOf(Content(creator, contentName, null, listOf(transport)))).send();
    }
}