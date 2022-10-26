/*
 * halcyon-core
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
package tigase.halcyon.core.configuration

import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.forms.JabberDataForm

data class Account(
	val userJID: BareJID,
	val resource: String?,
	val authzIdJID: BareJID?,
	val passwordCallback: () -> String,
)

data class Registration(
	val domain: String,
	val formHandler: ((JabberDataForm) -> Unit)?,
	val formHandlerWithResponse: ((JabberDataForm) -> JabberDataForm)?,
)

interface Connection

data class Configuration(val account: Account?, val connection: Connection, val registration: Registration? = null)

val Configuration.domain: String
	get() = this.account?.userJID?.domain ?: this.registration?.domain
	?: throw HalcyonException("Cannot determine domain.")