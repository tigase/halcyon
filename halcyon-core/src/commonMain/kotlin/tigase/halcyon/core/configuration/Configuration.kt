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

interface SaslConfig

interface UserJIDProvider {

    val userJID: BareJID

}

interface DomainProvider {

    val domain: String

}

data class Registration(
    override val domain: String,
    val formHandler: ((JabberDataForm) -> Unit)?,
    val formHandlerWithResponse: ((JabberDataForm) -> JabberDataForm)?,
) : DomainProvider

interface ConnectionConfig

data class Configuration(
    val sasl: SaslConfig?,
    val connection: ConnectionConfig,
    val registration: Registration? = null,
)

val Configuration.declaredDomain: String
    get() = if (this.sasl is DomainProvider) {
        this.sasl.domain
    } else if (this.registration != null) {
        this.registration.domain
    } else throw HalcyonException("Cannot determine domain.")

val Configuration.declaredUserJID: BareJID?
    get() = if (this.sasl is UserJIDProvider) {
        this.sasl.userJID
    } else null