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
package tigase.halcyon.core.connector

import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule

abstract class AbstractConnector(val halcyon: AbstractHalcyon) {

    protected var eventsEnabled = true

    var state: State = State.Disconnected
        protected set(value) {
            val old = field
            field = value
            if (old != field) fire(ConnectorStateChangeEvent(old, field))
        }

    abstract fun createSessionController(): SessionController

    abstract fun send(data: CharSequence)

    abstract fun start()

    abstract fun stop()

    protected fun handleReceivedElement(element: Element) {
        if (halcyon.getModuleOrNull(StreamManagementModule)?.processElementReceived(element) ==
            true
        ) {
            return
        }
        fire(ReceivedXMLElementEvent(element))
    }

    protected fun fire(e: Event) {
        if (eventsEnabled) halcyon.eventBus.fire(e)
    }
}
