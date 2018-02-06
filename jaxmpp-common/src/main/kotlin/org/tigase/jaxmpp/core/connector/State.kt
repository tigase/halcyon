package org.tigase.jaxmpp.core.connector

enum class State {
	/**
	 * Connection is established.
	 */
	Connected,
	/**
	 * Connector started establishing connection.
	 */
	Connecting,
	/**
	 * Connector is disconnected.
	 */
	Disconnected,
	/**
	 * Connector is closing connection and stopping workers.
	 */
	Disconnecting
}