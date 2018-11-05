package org.tigase.jaxmpp.core

actual fun currentTimestamp(): Long {
	return kotlin.js.Date.now().toLong();
}