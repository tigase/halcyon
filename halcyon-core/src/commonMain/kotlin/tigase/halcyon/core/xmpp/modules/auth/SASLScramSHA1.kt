package tigase.halcyon.core.xmpp.modules.auth

class SASLScramSHA1(randomGenerator: () -> String = { randomString(20) }) : AbstractSASLScram(
	name = "SCRAM-SHA-1",
	hashAlgorithm = ScramHashAlgorithm.SHA1,
	randomGenerator = randomGenerator,
	clientKeyData = "Client Key".encodeToByteArray(),
	serverKeyData = "Server Key".encodeToByteArray()
)
