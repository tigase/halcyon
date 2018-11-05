package org.tigase.jaxmpp.core

actual fun currentTimestamp(): Long {
	return System.currentTimeMillis();
}