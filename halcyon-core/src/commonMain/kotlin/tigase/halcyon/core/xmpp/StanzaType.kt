package tigase.halcyon.core.xmpp

enum class StanzaType {
	Chat,
	/**
	 * The stanza reports an error that has occurred regarding processing or
	 * delivery of a get or set request.
	 */
	Error,
	/**
	 * The stanza requests information, inquires about what data is needed in
	 * order to complete further operations, etc.
	 */
	Get,
	GroupChat,
	Headline,
	Normal,
	Probe,
	/**
	 * The stanza is a response to a successful get or set request.
	 */
	Result,
	/**
	 * The stanza provides data that is needed for an operation to be completed,
	 * sets new values, replaces existing values, etc.
	 */
	Set,
	Subscribe,
	Subscribed,
	Unavailable,
	Unsubscribe,
	Unsubscribed;
}