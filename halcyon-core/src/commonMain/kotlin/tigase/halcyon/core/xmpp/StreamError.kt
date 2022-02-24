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
package tigase.halcyon.core.xmpp

enum class StreamError(val elementName: String) { /**
 * The entity has sent XML that cannot be processed.
 */
BAD_FORMAT("bad-format"),
	BAD_NAMESPACE_PREFIX("bad-namespace-prefix"),
	CONFLICT("conflict"),
	CONNECTION_TIMEOUT("connection-timeout"),
	HOST_GONE("host-gone"),
	HOST_UNKNOWN("host-unknown"),
	IMPROPER_ADDRESSING("improper-addressing"),
	INTERNAL_SERVER_ERROR("internal-server-error"),
	INVALID_FROM("invalid-from"),
	INVALID_ID("invalid-id"),
	INVALID_NAMESPACE("invalid-namespace"),
	INVALID_XML("invalid-xml"),
	NOT_AUTHORIZED("not-authorized"),
	NOT_WELL_FORMED("not-well-formed"),
	POLICY_VIOLATION("policy-violation"),
	REMOTE_CONNECTION_FAILED("remote-connection-failed"),
	RESET("reset"),
	RESOURCE_CONSTRAINT("resource-constraint"),
	RESTRICTED_XML("restricted-xml"),

	/**
	 * The server will not provide service to the initiating entity but is redirecting traffic to another host
	 * under the administrative control	of the same service provider.
	 */
	SEE_OTHER_HOST("see-other-host"),
	SYSTEM_SHUTDOWN("system-shutdown"),
	UNDEFINED_CONDITION("undefined-condition"),
	UNSUPPORTED_ENCODING("unsupported-encoding"),
	UNSUPPORTED_STANZA_TYPE("unsupported-stanza-type"),
	UNSUPPORTED_VERSION("unsupported-version"),
	UNKNOWN_STREAM_ERROR("")
}