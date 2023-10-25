package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.Context
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory

data class AuthData(val mechanismName: String, val data: String?)

class SASLEngine(val context: Context) {

	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.auth.SASLEngine")

	val saslContext: SASLContext by context::authContext

	private val mechanisms: MutableList<SASLMechanism> = mutableListOf()

	private fun selectMechanism(allowedMechanisms: List<String>): SASLMechanism {
		for (mechanism in mechanisms) {
			log.finer { "Checking mechanism ${mechanism.name}" }
			if (allowedMechanisms.contains(mechanism.name) && mechanism.isAllowedToUse(
					context, context.config, saslContext
				)
			) {
				log.fine { "Selected mechanism: ${mechanism.name}" }
				return mechanism
			}
		}
		throw HalcyonException("None of known SASL mechanism is supported by server")
	}

	fun add(mechanism: SASLMechanism) = mechanisms.add(mechanism)

	fun start(allowedMechanisms: List<String>): AuthData {
		saslContext.state = State.InProgress
		val mechanism = selectMechanism(allowedMechanisms)
		val authData = mechanism.evaluateChallenge(null, context, context.config, saslContext)
		saslContext.mechanism = mechanism
		context.eventBus.fire(SASLEvent.SASLStarted(mechanism.name))
		return AuthData(mechanism.name, authData)
	}

	fun evaluateChallenge(data: String?): String? {
		val mechanism = saslContext.mechanism ?: throw ClientSaslException("SASL Context is empty")
		if (saslContext.complete) throw ClientSaslException("Mechanism ${mechanism.name} is finished but Server sent challenge.")
		val r = mechanism.evaluateChallenge(data, context, context.config, saslContext)
		return r
	}

	fun evaluateSuccess(data: String?) {
		val mechanism = saslContext.mechanism ?: throw ClientSaslException("SASL Context is empty")
		mechanism.evaluateChallenge(data, context, context.config, saslContext)
		if (saslContext.complete) {
			saslContext.state = State.Success
			context.eventBus.fire(SASLEvent.SASLSuccess())
		} else {
			saslContext.state = State.Failed
			throw ClientSaslException("Invalid state of SASL Engine")
		}
	}

	fun evaluateFailure(saslError: SASLModule.SASLError, errorText: String?) {
		saslContext.state = State.Failed
		context.eventBus.fire(SASLEvent.SASLError(saslError, errorText))
	}

}