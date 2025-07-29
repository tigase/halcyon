package tigase.halcyon.core.xmpp.modules.mam

import kotlinx.datetime.Instant
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.modules.RSM
import tigase.halcyon.core.xmpp.stanzas.Message

class QueryForMessages(
    private val mamModule: MAMModule,
    private val expectedBodies: Long,
    private val to: BareJID?,
    private val node: String?,
    private val initialRsm: RSM.Query?,
    private val with: String?,
    private val start: Instant?,
    private val end: Instant?,
    private val ignoreComplete: Boolean
) {

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.mam.QueryForMessages")

    private var consumerHandler: ((ForwardedStanza<Message>) -> Unit)? = null
    private var summaryHandler: ((RSM.Result) -> Unit)? = null

    private var messageBodiesCount = 0

    private var firstRSM: RSM.Result? = null
    private var lastRSM: RSM.Result? = null

    fun consumer(handler: (ForwardedStanza<Message>) -> Unit) {
        this.consumerHandler = handler
    }

    fun summary(handler: (RSM.Result) -> Unit) {
        this.summaryHandler = handler
    }

    init {
        run()
    }

    private fun finish() {
        val rsm = RSM.Result(
            first = firstRSM?.first,
            last = lastRSM?.last,
            index = firstRSM?.index,
            lastRSM?.count
        )
        summaryHandler?.invoke(rsm)
    }

    private fun processFin(complete: Boolean, rsm: RSM.Result?) {
        log.finest { "Received summary: complete=$complete; rsm=$rsm" }
        if (rsm?.first == null || ((!ignoreComplete) && complete)) {
            log.fine { "Finishing with complete=$complete" }
            finish()
        } else {
            firstRSM = rsm
            log.fine {
                "Preparing next iteration: receivedMessages=$messageBodiesCount; expected=$expectedBodies"
            }
            if (lastRSM == null) lastRSM = rsm
            if (messageBodiesCount < expectedBodies) {
                ask(
                    RSM.query {
                        if (initialRsm != null && initialRsm.after != null) {
                            after(rsm.last ?: "")
                        } else {
                            before(rsm.first ?: "")
                        }
                    }
                )
            } else {
                log.fine { "Finishing because enough messages have been received." }
                finish()
            }
        }
    }

    private fun ask(askRsm: RSM.Query?) {
        log.finer {
            "Preparing MAM request to=$to, node=$node, rsm=$askRsm, with=$with, start=$start, end=$end"
        }
        val req = mamModule.query(
            to = to,
            node = node,
            rsm = askRsm,
            with = with,
            start = start,
            end = end
        )
            .response {
                it.onSuccess { fin -> processFin(fin.complete, fin.rsm) }
            }
            .consume {
                it.stanza.body?.let {
                    ++messageBodiesCount
                }
                log.fine {
                    "Received message from MAM. hasBody=${it.stanza.body != null}; received=$messageBodiesCount"
                }
                consumerHandler?.invoke(it)
            }
            .send()
    }

    private fun run() {
        log.fine { "Start fetching MAM messages: expectedBodies=$expectedBodies" }
        ask(initialRsm)
    }
}

fun MAMModule.queryForMessages(
    expectedBodies: Long,
    to: BareJID? = null,
    node: String? = null,
    rsm: RSM.Query? = null,
    with: String? = null,
    start: Instant? = null,
    end: Instant? = null,
    ignoreComplete: Boolean = true,
    init: QueryForMessages.() -> Unit
) {
    val qfm =
        QueryForMessages(this, expectedBodies, to, node, rsm, with, start, end, ignoreComplete)
    qfm.init()
}
