package tigase.halcyon.core.xmpp.modules.auth

class SASLScramSHA1(randomGenerator: () -> String = { randomString() }) : AbstractSASLScram(
	name = "SCRAM-SHA-1", hashAlgorithm = ScramHashAlgorithm.SHA1, randomGenerator = randomGenerator
)

class SASLScramSHA1Plus(randomGenerator: () -> String = { randomString() }) : AbstractSASLScramPlus(
	name = "SCRAM-SHA-1-PLUS", hashAlgorithm = ScramHashAlgorithm.SHA1, randomGenerator = randomGenerator
)

class SASLScramSHA256(randomGenerator: () -> String = { randomString() }) : AbstractSASLScram(
	name = "SCRAM-SHA-256",
	hashAlgorithm = ScramHashAlgorithm.SHA256,
	randomGenerator = randomGenerator,
)

class SASLScramSHA256Plus(randomGenerator: () -> String = { randomString() }) : AbstractSASLScramPlus(
	name = "SCRAM-SHA-256-PLUS", hashAlgorithm = ScramHashAlgorithm.SHA256, randomGenerator = randomGenerator
)

class SASLScramSHA512(randomGenerator: () -> String = { randomString() }) : AbstractSASLScram(
	name = "SCRAM-SHA-512",
	hashAlgorithm = ScramHashAlgorithm.SHA512,
	randomGenerator = randomGenerator,
)

class SASLScramSHA512Plus(randomGenerator: () -> String = { randomString() }) : AbstractSASLScramPlus(
	name = "SCRAM-SHA-512-PLUS", hashAlgorithm = ScramHashAlgorithm.SHA512, randomGenerator = randomGenerator
)