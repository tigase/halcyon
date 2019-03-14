package tigase.halcyon.core

import tigase.halcyon.core.xmpp.BareJID

class SessionObject {

	enum class Scope {
		session,
		stream,
		user
	}

	class ClearedEvent(val scopes: Array<tigase.halcyon.core.SessionObject.Scope>) : tigase.halcyon.core.eventbus.Event(
		tigase.halcyon.core.SessionObject.ClearedEvent.Companion.TYPE
	) {
		companion object {
			const val TYPE = "SessionObject::ClearedEvent"
		}
	}

	lateinit var eventBus: tigase.halcyon.core.eventbus.EventBus

	private val log = tigase.halcyon.core.logger.Logger("tigase.halcyon.core.SessionObject")

	private val properties: MutableMap<String, tigase.halcyon.core.SessionObject.Entry> = HashMap()

	fun clear() {
		clear(tigase.halcyon.core.SessionObject.Scope.values())
	}

	fun clear(scopes: Array<tigase.halcyon.core.SessionObject.Scope>) {
			val iterator = this.properties.entries.iterator()
			while (iterator.hasNext()) {
				val entry = iterator.next()
				if (scopes.contains(entry.value.scope)) {
					iterator.remove()
				}
		}
		val event = tigase.halcyon.core.SessionObject.ClearedEvent(scopes)
		eventBus.fire(event)
	}

	fun <T> getProperty(scope: tigase.halcyon.core.SessionObject.Scope?, key: String): T? {
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
		return this.getProperty(tigase.halcyon.core.SessionObject.Companion.USER_BARE_JID) as BareJID?
	}

	/**
	 * {@inheritDoc}
	 */
	fun <T> getUserProperty(key: String): T? {
		return getProperty<T>(tigase.halcyon.core.SessionObject.Scope.user, key)
	}

	fun setProperty(
		scope: tigase.halcyon.core.SessionObject.Scope,
		key: String,
		value: Any?
	): tigase.halcyon.core.SessionObject {
			if (value == null) {
				this.properties.remove(key)
			} else {
				var e: tigase.halcyon.core.SessionObject.Entry? = this.properties.get(key)
				if (e == null) {
					e = tigase.halcyon.core.SessionObject.Entry()
					this.properties.put(key, e)
				}
				e.scope = scope
				e.value = value
		}
		return this
	}

	fun setProperty(key: String, value: Any?): tigase.halcyon.core.SessionObject {
		return setProperty(tigase.halcyon.core.SessionObject.Scope.session, key, value)
	}

	fun setUserProperty(key: String, value: Any?): tigase.halcyon.core.SessionObject {
		return setProperty(tigase.halcyon.core.SessionObject.Scope.user, key, value)
	}

	override fun toString(): String {
		return "AbstractSessionObject{" + "properties=" + properties + '}'.toString()
	}

	private class Entry {

		var scope: tigase.halcyon.core.SessionObject.Scope? = null
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