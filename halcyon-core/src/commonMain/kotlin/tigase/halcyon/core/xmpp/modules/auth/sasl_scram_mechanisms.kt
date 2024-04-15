package tigase.halcyon.core.xmpp.modules.auth

class SASLScramSHA1 : AbstractSASLScram(
    name = NAME,
    hashAlgorithm = ScramHashAlgorithm.SHA1
) {
    companion object : SASLMechanismProvider<SASLScramSHA1, SASLScramConfig> {
        override val NAME = "SCRAM-SHA-1"

        override fun instance(): SASLScramSHA1 = SASLScramSHA1()

        override fun configure(mechanism: SASLScramSHA1, cfg: SASLScramConfig.() -> Unit) = mechanism.cfg()
    }

}


class SASLScramSHA1Plus : AbstractSASLScramPlus(
    name = NAME, hashAlgorithm = ScramHashAlgorithm.SHA1
) {
    companion object : SASLMechanismProvider<SASLScramSHA1Plus, SASLScramConfig> {
        override val NAME = "SCRAM-SHA-1-PLUS"

        override fun instance(): SASLScramSHA1Plus = SASLScramSHA1Plus()

        override fun configure(mechanism: SASLScramSHA1Plus, cfg: SASLScramConfig.() -> Unit) = mechanism.cfg()
    }
}

class SASLScramSHA256 : AbstractSASLScram(
    name = NAME,
    hashAlgorithm = ScramHashAlgorithm.SHA256,
) {
    companion object : SASLMechanismProvider<SASLScramSHA256, SASLScramConfig> {
        override val NAME = "SCRAM-SHA-256"

        override fun instance(): SASLScramSHA256 = SASLScramSHA256()

        override fun configure(mechanism: SASLScramSHA256, cfg: SASLScramConfig.() -> Unit) = mechanism.cfg()
    }
}

class SASLScramSHA256Plus : AbstractSASLScramPlus(
    name = NAME, hashAlgorithm = ScramHashAlgorithm.SHA256
) {
    companion object : SASLMechanismProvider<SASLScramSHA256Plus, SASLScramConfig> {
        override val NAME = "SCRAM-SHA-256-PLUS"

        override fun instance(): SASLScramSHA256Plus = SASLScramSHA256Plus()

        override fun configure(mechanism: SASLScramSHA256Plus, cfg: SASLScramConfig.() -> Unit) = mechanism.cfg()
    }
}

class SASLScramSHA512 : AbstractSASLScram(
    name = NAME,
    hashAlgorithm = ScramHashAlgorithm.SHA512,
) {
    companion object : SASLMechanismProvider<SASLScramSHA512, SASLScramConfig> {
        override val NAME = "SCRAM-SHA-512"

        override fun instance(): SASLScramSHA512 = SASLScramSHA512()

        override fun configure(mechanism: SASLScramSHA512, cfg: SASLScramConfig.() -> Unit) = mechanism.cfg()
    }
}

class SASLScramSHA512Plus : AbstractSASLScramPlus(
    name = NAME, hashAlgorithm = ScramHashAlgorithm.SHA512
) {
    companion object : SASLMechanismProvider<SASLScramSHA512Plus, SASLScramConfig> {
        override val NAME = "SCRAM-SHA-512-PLUS"

        override fun instance(): SASLScramSHA512Plus = SASLScramSHA512Plus()

        override fun configure(mechanism: SASLScramSHA512Plus, cfg: SASLScramConfig.() -> Unit) = mechanism.cfg()
    }
}