package tigase.halcyon.coroutines

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xmpp.stanzas.Stanza

@ExperimentalCoroutinesApi
suspend fun <V, STT : Stanza<*>> RequestBuilder<V, STT>.awaitResponse(): V =
    suspendCancellableCoroutine<V> { continuation ->
        this.response { result ->
            result.onFailure {
                continuation.cancel(it)
            }
            result.onSuccess {
                continuation.resume(it, null)
            }
        }
            .send()
    }

@ExperimentalCoroutinesApi
suspend fun <V, STT : Stanza<*>> RequestBuilder<V, STT>.send(context: CoroutineContext) =
    withContext(context) {
        send()
    }

@ExperimentalCoroutinesApi
suspend fun <V, STT : Stanza<*>> RequestBuilder<V, STT>.execute(context: CoroutineContext) {
    val that = this
    return withContext(context) {
        suspendCancellableCoroutine<V> { continuation ->
            that.response { result ->
                result.onFailure {
                    continuation.cancel(it)
                }
                result.onSuccess {
                    continuation.resume(it, null)
                }
            }
                .send()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <V> CoroutineContext.execute(block: ((Result<V>) -> Unit) -> Unit): V =
    withContext(this) {
        suspendCancellableCoroutine { continuation ->
            block { result ->
                result.onFailure {
                    continuation.cancel(it)
                }
                result.onSuccess {
                    continuation.resume(it, null)
                }
            }
        }
    }
