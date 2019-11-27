/*
 * Tigase Halcyon XMPP Library
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core

import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventBus
import tigase.halcyon.core.logger.Logger
import tigase.halcyon.core.xmpp.BareJID

class SessionObject {

	enum class Scope {
		Session,
		Stream,
		User
	}

	data class ClearedEvent(val scopes: Array<Scope>) : Event(TYPE) {

		companion object {
			const val TYPE = "SessionObject::ClearedEvent"
		}

		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is ClearedEvent) return false

			if (!scopes.contentEquals(other.scopes)) return false

			return true
		}

		override fun hashCode(): Int {
			return scopes.contentHashCode()
		}
	}

	lateinit var eventBus: EventBus

	private val log = Logger("tigase.halcyon.core.SessionObject")

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

	@Suppress("UNCHECKED_CAST")
	fun <T> getProperty(scope: Scope?, key: String): T? {
		val entry = this.properties[key]
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
		return getProperty<T>(Scope.User, key)
	}

	fun setProperty(
		scope: Scope, key: String, value: Any?
	): SessionObject {
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
		return setProperty(Scope.Session, key, value)
	}

	fun setUserProperty(key: String, value: Any?): SessionObject {
		return setProperty(Scope.User, key, value)
	}

	override fun toString(): String {
		return "AbstractSessionObject{properties=$properties}"
	}

	private class Entry {

		var scope: Scope? = null
		var value: Any? = null

		override fun toString(): String {
			return "Entry{scope=$scope, value=$value}"
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