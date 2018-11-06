package org.tigase.jaxmpp.core

import org.tigase.jaxmpp.core.eventbus.Event
import org.tigase.jaxmpp.core.eventbus.EventBus
import org.tigase.jaxmpp.core.logger.Logger
import org.tigase.jaxmpp.core.xmpp.BareJID

class SessionObject {

	enum class Scope {
		session,
		stream,
		user
	}

	class ClearedEvent(val scopes: Array<Scope>) : Event(TYPE) {
		companion object {
			const val TYPE = "SessionObject::ClearedEvent"
		}
	}

	lateinit var eventBus: EventBus

	private val log = Logger("org.tigase.jaxmpp.core.SessionObject")

	private val properties: MutableMap<String, Entry> = HashMap()

	fun clear() {
		clear(Scope.values())
	}

	fun clear(scopes: Array<Scope>) {
			val iterator = this.properties.entries.iterator()
			while (iterator.hasNext()) {
				val entry = iterator.next()
				if (scopes.contains(entry.value.scope)) {
					iterator.remove()
				}
		}
		val event = ClearedEvent(scopes)
		eventBus.fire(event)
	}

	fun <T> getProperty(scope: Scope?, key: String): T? {
			val entry = this.properties.get(key)
			return if (entry == null) {
				null
			} else if (scope == null || scope == entry.scope) {
				entry.value as T?
			} else {
				null
		}
	}

	/**
	 * {@inheritDoc}
	 */
	fun <T> getProperty(key: String): T? {
		return getProperty<T>(null, key)
	}

	/**
	 * {@inheritDoc}
	 */
	fun getUserBareJid(): BareJID? {
		return this.getProperty(USER_BARE_JID) as BareJID?
	}

	/**
	 * {@inheritDoc}
	 */
	fun <T> getUserProperty(key: String): T? {
		return getProperty<T>(Scope.user, key)
	}

	fun setProperty(scope: Scope, key: String, value: Any?): SessionObject {
			if (value == null) {
				this.properties.remove(key)
			} else {
				var e: Entry? = this.properties.get(key)
				if (e == null) {
					e = Entry()
					this.properties.put(key, e)
				}
				e.scope = scope
				e.value = value
		}
		return this
	}

	fun setProperty(key: String, value: Any?): SessionObject {
		return setProperty(Scope.session, key, value)
	}

	fun setUserProperty(key: String, value: Any?): SessionObject {
		return setProperty(Scope.user, key, value)
	}

	override fun toString(): String {
		return "AbstractSessionObject{" + "properties=" + properties + '}'.toString()
	}

	private class Entry {

		var scope: Scope? = null
		var value: Any? = null

		override fun toString(): String {
			return "Entry{" + "scope=" + scope + ", value=" + value + '}'.toString()
		}
	}

	companion object {
		/**
		 * Name of property used to keep logical name of XMPP server. Usually it is
		 * equals to hostname of users JID.
		 */
		const val DOMAIN_NAME = "domainName"

		/**
		 * Name of property used to keep users nickname
		 */
		const val NICKNAME = "nickname"

		/**
		 * Name of property used to keep users password
		 */
		const val PASSWORD = "password"

		/**
		 * Name of property used to keep XMPP resource
		 */
		const val RESOURCE = "resource"

		/**
		 * Name of property used to keep users JID
		 */
		const val USER_BARE_JID = "userBareJid"

	}

}