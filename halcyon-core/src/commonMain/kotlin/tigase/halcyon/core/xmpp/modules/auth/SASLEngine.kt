package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.Context
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xml.Element

data class AuthData(val mechanismName: String, val data: String?)


interface MechanismsConfiguration {

    /**
     * Install new SASL mechanism.
     */
    fun <MECH : SASLMechanism, CFG : Any> install(
        provider: SASLMechanismProvider<MECH, CFG>,
        configuration: CFG.() -> Unit = {}
    )

    /**
     * Remove installed SASL mechanism.
     */
    fun <MECH : SASLMechanism, CFG : Any> uninstall(
        provider: SASLMechanismProvider<MECH, CFG>
    )

}

class SASLEngine(val context: Context) : MechanismsConfiguration {

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.auth.SASLEngine")

    val saslContext: SASLContext by context::authContext

    private val mechanisms: MutableList<SASLMechanism> = mutableListOf()

    init {
        install(SASLScramSHA512Plus)
        install(SASLScramSHA256Plus)
        install(SASLScramSHA1Plus)
        install(SASLScramSHA512)
        install(SASLScramSHA256)
        install(SASLScramSHA1)
        install(SASLPlain)
    }

    private fun selectMechanism(allowedMechanisms: List<String>, streamFeatures: Element): SASLMechanism {
        for (mechanism in mechanisms) {
            log.finer { "Checking mechanism ${mechanism.name}" }
            if (allowedMechanisms.contains(mechanism.name) && mechanism.isAllowedToUse(
                    context, context.config, saslContext, streamFeatures
                )
            ) {
                log.fine { "Selected mechanism: ${mechanism.name}" }
                return mechanism
            }
        }
        throw HalcyonException("None of known SASL mechanism is supported by server")
    }

    override fun <MECH : SASLMechanism, CFG : Any> install(
        provider: SASLMechanismProvider<MECH, CFG>,
        configuration: CFG.() -> Unit
    ) {
        val m = provider.instance()
        provider.configure(m, configuration)
        this.mechanisms.add(m)
    }

    override fun <MECH : SASLMechanism, CFG : Any> uninstall(provider: SASLMechanismProvider<MECH, CFG>) {
        this.mechanisms.removeAll { mech -> mech.name == provider.NAME }
    }

    fun start(allowedMechanismsNames: List<String>, streamFeatures: Element): AuthData {
        saslContext.state = State.InProgress
        val mechanism = selectMechanism(allowedMechanismsNames, streamFeatures)
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

    fun removeAllMechanisms() = this.mechanisms.clear()

    fun checkMechanisms(allowedMechanisms: List<String>): Boolean {
        return this.mechanisms.map { it.name }.any { allowedMechanisms.contains(it) }
    }

}