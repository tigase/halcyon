package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.Context
import tigase.halcyon.core.Scope
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.property

data class AuthData(val mechanismName: String, val data: String?)

class SASLEngine(val context: Context) {

	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.auth.SASLEngine")

	val saslContext: SASLContext by context::authContext

	private val mechanisms: MutableList<SASLMechanism> = mutableListOf()

	private fun selectMechanism(): SASLMechanism {
		for (mechanism in mechanisms) {
			log.finer { "Checking mechanism ${mechanism.name}" }
			if (mechanism.isAllowedToUse(context.config, saslContext)) {
				log.fine { "Selected mechanism: ${mechanism.name}" }
				return mechanism
			}
		}
		throw HalcyonException("None of known SASL mechanism is supported by server")
	}

	fun add(mechanism: SASLMechanism) = mechanisms.add(mechanism)

	fun start(): AuthData {
		saslContext.state = State.InProgress
		val mechanism = selectMechanism()
		val authData = mechanism.evaluateChallenge(null, context.config, saslContext)
		saslContext.mechanism = mechanism
		context.eventBus.fire(SASLEvent.SASLStarted(mechanism.name))
		return AuthData(mechanism.name, authData)
	}

	fun evaluateChallenge(data: String?): String? {
		val mechanism = saslContext.mechanism ?: throw HalcyonException("SASL Context is empty")
		if (saslContext.complete) throw HalcyonException("Mechanism ${mechanism.name} is finished but Server sent challenge.")
		val r = mechanism.evaluateChallenge(data, context.config, saslContext)
		return r
	}

	fun evaluateSuccess(data: String?) {
		saslContext.state = State.Success
		context.eventBus.fire(SASLEvent.SASLSuccess())
	}

	fun evaluateFailure(saslError: SASLModule.SASLError, errorText: String?) {
		saslContext.state = State.Failed
		context.eventBus.fire(SASLEvent.SASLError(saslError, errorText))
	}

}