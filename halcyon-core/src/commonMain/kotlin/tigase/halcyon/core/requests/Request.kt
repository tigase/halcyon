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

import kotlinx.datetime.Instant
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.JID
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.stanzas.Stanza
import tigase.halcyon.core.xmpp.stanzas.wrap
import kotlin.time.Duration

class Request<V, STT : Stanza<*>>(
    val jid: JID?,
    val id: String,
    val creationTimestamp: Instant,
    val stanza: STT,
    var timeoutDelay: Duration,
    private val handler: ResultHandler<V>?,
    private val transform: (value: Any) -> V,
    private val errorHandler: (STT) -> XMPPError,
    private val parentRequest: Request<*, STT>? = null,
    private val onSendHandler: SendHandler<V, STT>?,
) {

    private val log = LoggerFactory.logger("tigase.halcyon.core.requests.Request")

    data class Error(val condition: ErrorCondition, val message: String?)

    private val data = HashMap<String, Any>()

    internal var requestName: String? = null

    /**
     * `true` when no response for IQ or when stanza is not delivered to server (StreamManagement must be enabled)
     */
    var isTimeout: Boolean = false
        private set

    var isCompleted: Boolean = false
        private set

    var isSent: Boolean = false
        private set

    var response: STT? = null
        private set

    private var calculatedResult: Result<V>? = null

    internal var stanzaHandler: ResponseStanzaHandler<STT>? = null

    private fun requestStack(): List<Request<*, STT>> {
        val result = mutableListOf<Request<*, STT>>()

        var tmp: Request<*, STT>? = this
        while (tmp != null) {
            result.add(0, tmp)
            tmp = tmp.parentRequest
        }

        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun processStack() {
        val requests = requestStack()

        // Currently returned value. Will be updated by map()'s from stack.
        var tmpValue: Any? = response
        // Result calculated in previous step
        var tmpResult: Result<Any?>? = null

        requests.forEach { req ->
            tmpResult = if (tmpResult != null && (tmpResult as Result<Any?>).isFailure) tmpResult else if (isTimeout) {
                Result.failure(XMPPError(response, ErrorCondition.RemoteServerTimeout, "No response for request ${req.id}"))
            } else {
                when (response!!.attributes["type"]) {
                    "result" -> {
                        try {
                            log.finest { "Mapping response in ${this@Request}" }
                            tmpValue = req.transform.invoke(tmpValue!!)
                            log.finest { "Mapping response finished in ${this@Request}" }
                            Result.success(tmpValue)
                        } catch (e: XMPPError) {
//							log.warning(e) { "Mapping response error in ${this@Request}" }
                            Result.failure<XMPPError>(e)
                        } catch (e: XMPPException) {
                            Result.failure<XMPPError>(XMPPError(req.response, e.condition, e.message))
                        } catch (e: Throwable) {
                            log.warning(e) { "Mapping response error in ${this@Request}" }
                            Result.failure<Throwable>(e)
                        }
                    }

                    "error" -> {
                        val z = errorHandler.invoke(response!!)
                        Result.failure<XMPPError>(z)
//						val e = findCondition(response!!)
//						Result.failure(XMPPError(response!!, e.condition, e.message))
                    }

                    else -> {
                        Result.failure(XMPPError(response!!, ErrorCondition.UnexpectedRequest, null))
                    }
                }
            }

            req.calculatedResult = tmpResult as (Result<Nothing>)
            if (isTimeout) req.markTimeout(false)
            else req.setResponseStanza(response!!, false)
        }
    }

    fun setResponseStanza(response: Element, processStack: Boolean = true) {
        log.finest { "Setting response in ${this@Request}" }
        this.response = wrap(response)
        isCompleted = true
        if (processStack) processStack() else callHandlers()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun markAsSent() {
        var tmp: Any? = Unit
        requestStack().forEach { req ->
            log.finest { "Marking as sent ${this@Request}" }
            req.isSent = true

            req.onSendHandler?.invoke(req)
        }
    }

    internal fun markTimeout(processStack: Boolean = true) {
        log.finest { "Marking as timeout ${this@Request}" }
        isCompleted = true
        isTimeout = true
        if (processStack) processStack() else callHandlers()
    }

    private fun callHandlers() {
        callResponseStanzaHandler()
        val tmp = calculatedResult ?: throw RuntimeException("No calculated result")
        callResponseHandler(tmp)
    }

    private fun callResponseHandler(tmp: Result<V>) {
        handler?.let { h ->
            try {
                log.finest { "Executing handler in ${this@Request}" }
                h.invoke(tmp)
                log.finest { "Executing handler finished in ${this@Request}" }
            } catch (e: Throwable) {
                log.warning(e) {
                    "Problem inside handler in ${this@Request}"
                }
            }
        }
    }

    private fun callResponseStanzaHandler() {
        stanzaHandler?.let { h ->
            try {
                log.finest { "Executing stanza handler in ${this@Request}" }
                h.invoke(this, response)
                log.finest { "Executing stanza handler finished in ${this@Request}" }
            } catch (e: Throwable) {
                log.warning(e) { "Problem inside stanza handler in ${this@Request}" }
            }
        }
    }

    @Suppress("unused")
    fun isSet(param: String): Boolean {
        val v = data[param]
        return v != null && v is Boolean && v
    }

    @Suppress("unused")
    fun setData(name: String, value: Any) {
        data[name] = value
    }

    @Suppress("unused")
    fun getData(name: String): Any? {
        return data[name]
    }

    override fun toString(): String {
        return buildString {
            append("Request(")
            requestName?.let {
                append("name='$requestName', ")
            }
            append("stanza=")
            append(stanza.getAsString())
            append(")")
        }
    }

}
