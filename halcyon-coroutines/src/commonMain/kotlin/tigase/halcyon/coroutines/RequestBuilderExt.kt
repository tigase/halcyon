package tigase.halcyon.coroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
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
