package tigase.halcyon.core.xmpp.modules.auth

class SASLScramSHA256(randomGenerator: () -> String = { randomString(20) }) : AbstractSASLScram(
	name = "SCRAM-SHA-256",
	hashAlgorithm = ScramHashAlgorithm.SHA256,
	randomGenerator = randomGenerator,
	clientKeyData = "Client Key".encodeToByteArray(),
	serverKeyData = "Server Key".encodeToByteArray()
)
