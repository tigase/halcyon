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
package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element

sealed class AuthEvent(type: String) : tigase.halcyon.core.eventbus.Event(type) {
	data class AuthStartedEvent(val mechanism: String) : AuthEvent(
		tigase.halcyon.core.xmpp.modules.auth.AuthEvent.AuthStartedEvent.TYPE
	) {

		companion object {
			const val TYPE = "tigase.halcyon.core.xmpp.modules.auth.AuthEvent.AuthStartedEvent"
		}
	}

	class AuthSuccessEvent : AuthEvent(tigase.halcyon.core.xmpp.modules.auth.AuthEvent.AuthSuccessEvent.TYPE) {

		companion object {
			const val TYPE = "tigase.halcyon.core.xmpp.modules.auth.AuthEvent.AuthSuccessEvent"
		}
	}

	data class AuthErrorEvent(val error: SaslModule.SaslError, val description: String?) : AuthEvent(
		tigase.halcyon.core.xmpp.modules.auth.AuthEvent.AuthErrorEvent.TYPE
	) {

		companion object {
			const val TYPE = "tigase.halcyon.core.xmpp.modules.auth.AuthEvent.AuthErrorEvent"
		}
	}
}

class SaslModule : tigase.halcyon.core.modules.XmppModule {


	enum class SaslError(val elementName: String) {

		/**
		 * The receiving entity acknowledges an &lt;abort/&gt; element sent by
		 * the initiating entity; sent in reply to the &lt;abort/&gt; element.
		 */
		Aborted("aborted"),
		/**
		 * The data provided by the initiating entity could not be processed
		 * because the BASE64 encoding is incorrect (e.g., because the encoding
		 * does not adhere to the definition in Section 3 of BASE64); sent in
		 * reply to a &lt;response/&gt; element or an &lt;auth/&gt; element with
		 * initial response data.
		 */
		IncorrectEncoding("incorrect-encoding"),
		/**
		 * The authzid provided by the initiating entity is invalid, either
		 * because it is incorrectly formatted or because the initiating entity
		 * does not have permissions to authorize that ID; sent in reply to a
		 * &lt;response/&gt element or an &lt;auth/&gt element with initial
		 * response data.
		 */
		InvalidAuthzid("invalid-authzid"),
		/**
		 * The initiating entity did not provide a mechanism or requested a
		 * mechanism that is not supported by the receiving entity; sent in
		 * reply to an &lt;auth/&gt element.
		 */
		InvalidMechanism("invalid-mechanism"),
		/**
		 * The mechanism requested by the initiating entity is weaker than
		 * server policy permits for that initiating entity; sent in reply to a
		 * &lt;response/&gt element or an &lt;auth/&gt element with initial
		 * response data.
		 */
		MechanismTooWeak("mechanism-too-weak"),
		/**
		 * he authentication failed because the initiating entity did not
		 * provide valid credentials (this includes but is not limited to the
		 * case of an unknown username); sent in reply to a &lt;response/&gt
		 * element or an &lt;auth/&gt element with initial response data.
		 */
		NotAuthorized("not-authorized"),
		ServerNotTrusted("server-not-trusted"),
		/**
		 * The authentication failed because of a temporary error condition
		 * within the receiving entity; sent in reply to an &lt;auth/&gt element
		 * or &lt;response/&gt element.
		 */
		TemporaryAuthFailure("temporary-auth-failure");

		companion object {
			fun valueByElementName(elementName: String): SaslError? {
				return values().firstOrNull { saslError -> saslError.elementName == elementName }
			}
		}

	}

	enum class State {
		Unknown,
		InProgress,
		Success,
		Failed
	}

	companion object {
		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-sasl"
		const val TYPE = "SaslModule"
		const val AUTH_STATE = "SaslModule.AuthState"

		fun getAuthState(sessionObject: tigase.halcyon.core.SessionObject): State = sessionObject.getProperty<State>(
			tigase.halcyon.core.SessionObject.Scope.session, AUTH_STATE
		) ?: State.Unknown
	}

	override val type = TYPE
	override lateinit var context: tigase.halcyon.core.Context
	override val criteria = tigase.halcyon.core.modules.Criterion.or(
		tigase.halcyon.core.modules.Criterion.nameAndXmlns("success", XMLNS),
		tigase.halcyon.core.modules.Criterion.nameAndXmlns("failure", XMLNS),
		tigase.halcyon.core.modules.Criterion.nameAndXmlns("challenge", XMLNS)
	)
	override val features: Array<String>? = null

	override fun initialize() {}

	fun startAuth() {
		val username = context.sessionObject.getUserBareJid()?.localpart!!
		val password = context.sessionObject.getProperty<String>(tigase.halcyon.core.SessionObject.PASSWORD)!!
		val mechanism = "PLAIN"
//		context.sessionObject.setProperty(AUTH_STATE, )
		val authElement = element("auth") {
			xmlns = XMLNS
			attribute("mechanism", mechanism)
			+tigase.halcyon.core.Base64.encode('\u0000' + username + '\u0000' + password)
		}
		context.writer.writeDirectly(authElement)
		context.sessionObject.setProperty(tigase.halcyon.core.SessionObject.Scope.session, AUTH_STATE, State.InProgress)
		context.eventBus.fire(AuthEvent.AuthStartedEvent(mechanism))
	}

	override fun process(element: Element) {
		when (element.name) {
			"success" -> {
				context.sessionObject.setProperty(
					tigase.halcyon.core.SessionObject.Scope.session, AUTH_STATE, State.Success
				)
				context.eventBus.fire(AuthEvent.AuthSuccessEvent())
			}
			"failure" -> {
				val errElement = element.getFirstChild()!!
				val saslError = SaslError.valueByElementName(errElement.name)!!

				var errorText: String? = null
				element.getFirstChild("text")?.apply {
					errorText = this.value
				}

				context.sessionObject.setProperty(
					tigase.halcyon.core.SessionObject.Scope.session, AUTH_STATE, State.Failed
				)
				context.eventBus.fire(AuthEvent.AuthErrorEvent(saslError, errorText))
			}
			else -> throw tigase.halcyon.core.exceptions.HalcyonException("Unsupported element")
		}
	}
}