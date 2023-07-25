package tigase.halcyon.rx

import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.single
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.modules.serviceFinder.ServiceFinderModule

fun ServiceFinderModule.findAll(predicate: (DiscoveryModule.Info) -> Boolean = { true }): Single<List<DiscoveryModule.Info>> =
	single { emitter ->
		try {
			this.findComponents(predicate = predicate, resultHandler = {
				it.onSuccess { emitter.onSuccess(it) }
				it.onFailure { emitter.onError(it) }
			})
		} catch (e: Throwable) {
			emitter.onError(e)
		}
	}