package tigase.halcyon.rx

import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.single
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xmpp.stanzas.Stanza

fun <V, STT : Stanza<*>> RequestBuilder<V, STT>.asSingle(): Single<V> = single<V>(onSubscribe = { emitter ->
	try {
		this@asSingle.response { result ->
			result.onSuccess {
				emitter.onSuccess(it)
			}
			result.onFailure {
				emitter.onError(it)
			}
		}
		this@asSingle.send()
	} catch (e: Throwable) {
		emitter.onError(e)
	}
})