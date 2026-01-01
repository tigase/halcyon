package tigase.halcyon.coroutines

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xmpp.stanzas.Stanza
import kotlin.coroutines.CoroutineContext

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

suspend fun <V, STT : Stanza<*>> RequestBuilder<V, STT>.send(context: CoroutineContext) {
	return withContext(context) {
		send();
	}
}

suspend fun <V, STT : Stanza<*>> RequestBuilder<V, STT>.execute(context: CoroutineContext) {
	val that = this;
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

suspend fun <V> CoroutineContext.execute(block: ((Result<V>)->Unit)->Unit): V {
	return withContext(this) {
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
}
