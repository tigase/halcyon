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
package tigase.halcyon.core.xmpp.modules.chatstates

import kotlinx.datetime.Clock
import tigase.halcyon.core.TickEvent
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventBus
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.eventbus.EventHandler
import tigase.halcyon.core.xmpp.BareJID
import kotlin.time.Duration.Companion.seconds

data class OwnChatStateChangeEvent(
	val jid: BareJID, val oldState: ChatState, val state: ChatState, val sendUpdate: Boolean,
) : Event(TYPE) {

	companion object : EventDefinition<OwnChatStateChangeEvent> {

		override val TYPE = "tigase.halcyon.core.xmpp.modules.chatstates.OwnChatStateChangeEvent"
	}
}

class ChatStateMachine(val jid: BareJID, private val eventBus: EventBus, var sendUpdates: Boolean = false) :
	EventHandler<TickEvent> {

	var currentState: ChatState = ChatState.Inactive
		private set
	private var updateTime = Clock.System.now()

	private fun setNewState(newState: ChatState, allowedToSendUpdate: Boolean) {
		val oldState = currentState
		updateTime = Clock.System.now()
		if (currentState != newState) {
			currentState = newState
			eventBus.fire(OwnChatStateChangeEvent(jid, oldState, newState, sendUpdates && allowedToSendUpdate))
		}
	}

	fun update() {
		val now = Clock.System.now()
		when {
			currentState == ChatState.Active && updateTime < now - 120.seconds -> ChatState.Inactive
			currentState == ChatState.Inactive && updateTime < now - 600.seconds -> ChatState.Gone
			currentState == ChatState.Composing && updateTime < now - 30.seconds -> ChatState.Paused
			else -> null
		}?.let { calculatedState ->
			setNewState(calculatedState, true)
		}
	}

	/**
	 * User activated chat window.
	 */
	fun focused() {
		setNewState(ChatState.Active, true)
	}

	/**
	 * User deactivated chat window.
	 */
	fun focusLost() {
		setNewState(ChatState.Inactive, true)
	}

	/**
	 * Chat window is closed by user.
	 */
	fun closeChat() {
		setNewState(ChatState.Gone, true)
	}

	/**
	 * User composing an message. Function may be called every key press.
	 */
	fun composing() {
		setNewState(ChatState.Composing, true)
	}

	/**
	 *
	 */
	fun sendingMessage() {
		setNewState(ChatState.Active, false)
	}

	override fun onEvent(event: TickEvent) {
		update()
	}

}