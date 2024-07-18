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
package tigase.halcyon.core.requests

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.utils.Lock
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.FullJID
import tigase.halcyon.core.xmpp.getFromAttr
import tigase.halcyon.core.xmpp.stanzas.IQ

class RequestsManager {

    private val log = LoggerFactory.logger("tigase.halcyon.core.requests.RequestsManager")

    private val executor = tigase.halcyon.core.excutor.Executor()

    private val requests = HashMap<String, Request<*, *>>()
    private val lock = Lock();

    fun register(request: Request<*, *>) {
        if (request.stanza.name == IQ.NAME) {
            lock.withLock {
                requests[key(request.stanza)] = request
            }
        }
    }

    var boundJID: FullJID? = null

    private fun key(element: Element): String = "${element.name}:${element.attributes["id"]}"

    fun getRequest(response: Element): Request<*, *>? {
        val id = key(response)

        return lock.withLock {
            val request = requests[id] ?: return@withLock null

            if (verify(request, response)) {
                requests.remove(id)
                return@withLock request
            } else {
                return@withLock null
            }
        }
    }

    private fun verify(entry: Request<*, *>, response: Element): Boolean {
        val jid = response.getFromAttr()
        val bareBoundJID = boundJID?.bareJID

        if (jid == entry.jid) return true
        else if (entry.jid == null && bareBoundJID != null && jid?.bareJID == bareBoundJID) return true

        return false
    }

    fun findAndExecute(response: Element): Boolean {
        val r: Request<*, *> = getRequest(response) ?: return false
        execute { r.setResponseStanza(response) }
        return true
    }

    private fun execute(runnable: () -> Unit) {
        executor.execute {
            try {
                runnable.invoke()
            } catch (e: Throwable) {
                log.warning(e) { "Error on processing response" }
            }
        }
    }

    fun timeoutAll(maxCreationTimestamp: Instant = Instant.DISTANT_FUTURE) {
        log.info { "Timeout all waiting requests" }

        val toTimeout = lock.withLock {
            requests.entries.filter {
                it.value.creationTimestamp < maxCreationTimestamp
            };
        }
        toTimeout.forEach {
            lock.withLock {
                requests.remove(it.key)
            }
            // TODO: somehow this causes exception on iOS... is this thread safe??
            if (!it.value.isCompleted) {
                execute { it.value.markTimeout() }
            }
        }
    }

    fun findOutdated() {
        val now = Clock.System.now()
        val toRemove = lock.withLock {
            requests.entries.filter {
                it.value.isCompleted || it.value.creationTimestamp + it.value.timeoutDelay <= now
            }
        }
        toRemove.forEach { (key, request) ->
            lock.withLock {
                requests.remove(key)
            }
            if (request.creationTimestamp + request.timeoutDelay <= now) {
                execute(request::markTimeout)
            }
        }
    }

    fun getWaitingRequestsSize(): Int = lock.withLock { requests.size }
    fun getRequestsIDs(): String = lock.withLock { requests.values.map { it.id }.joinToString { it } }

}