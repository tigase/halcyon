package tigase.halcyon.rx

import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.single
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xmpp.stanzas.Stanza

private val log = LoggerFactory.logger("tigase.halcyon.rx.asSingle")
fun <V, STT : Stanza<*>> RequestBuilder<V, STT>.asSingle(): Single<V> = single<V>(onSubscribe = { emitter ->
    try {
        this@asSingle.response { result ->
            result.onSuccess {
                emitter.onSuccess(it)
            }
            result.onFailure {
                if (log.isLoggable(Level.FINER))
                    log.log(Level.FINER, "Handling error response for request.", it)

                emitter.onError(it)
            }
        }
        this@asSingle.send()
    } catch (e: Throwable) {
        log.warning(e) { "Something goes wrong in RX request processing." }
        emitter.onError(e)
    }
})